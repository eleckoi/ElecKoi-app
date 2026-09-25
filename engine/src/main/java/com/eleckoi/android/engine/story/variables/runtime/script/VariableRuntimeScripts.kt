package com.eleckoi.android.engine.story.variables.runtime.script

import org.json.JSONObject

/** JavaScript sources executed by [VariableRuntimeService][com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService]. */
internal object VariableRuntimeScripts {
    const val ZodBundleAssetPath = "variable-runtime/zod.bundle.js"

    val helpers = """
        globalThis.__eleckoiPathSegments = function(path) {
          const normalized = String(path ?? '').trim();
          if (!normalized) return [];
          return normalized
            .replace(/\[\s*["']?([^\]"']+)["']?\s*\]/g, '.$1')
            .split('.')
            .map(value => value.trim())
            .filter(Boolean);
        };

        globalThis.__eleckoiReadPath = function(root, path) {
          const segments = __eleckoiPathSegments(path);
          let value = root;
          for (const segment of segments) {
            if (value == null || !Object.prototype.hasOwnProperty.call(Object(value), segment)) {
              return undefined;
            }
            value = value[segment];
          }
          return value;
        };

        globalThis.__eleckoiCreateGetvar = function(state) {
          return function getvar(key, options = {}) {
            if (key == null || String(key).trim() === '') return state;
            const segments = __eleckoiPathSegments(key);
            if (segments[0] === 'stat_data') segments.shift();
            const value = __eleckoiReadPath(state, segments.join('.'));
            return value === undefined && Object.prototype.hasOwnProperty.call(options ?? {}, 'defaults')
              ? options.defaults
              : value;
          };
        };
    """.trimIndent()

    val ejsRuntime = """
        globalThis.__eleckoiRenderTemplates = async function(input) {
          try {
            const sourceById = new Map(input.sources.map(source => [source.id, source]));
            const sourceByName = new Map();
            for (const source of input.sources) {
              if (source.title) sourceByName.set(`${ '$' }{source.controller_id}\u0000${ '$' }{source.title}`, source);
              if (source.path) sourceByName.set(`${ '$' }{source.controller_id}\u0000${ '$' }{source.path}`, source);
            }
            const getvar = __eleckoiCreateGetvar(input.state);
            const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor;
            const renderStack = [];

            const toText = value => value == null ? '' : String(value);
            const asPattern = value => {
              if (value instanceof RegExp) return value;
              const text = String(value ?? '');
              const literal = text.match(/^\/(.*)\/([dgimsuvy]*)$/);
              try { return literal ? new RegExp(literal[1], literal[2]) : new RegExp(text); }
              catch (_) { return { test: candidate => String(candidate).includes(text) }; }
            };
            const matchChatMessages = (pattern, options = {}) => {
              let messages = input.messages;
              if (options.role) messages = messages.filter(message => message.role === options.role);
              const start = Number.isInteger(options.start) ? options.start : -2;
              const end = Number.isInteger(options.end) ? options.end : messages.length;
              const from = start < 0 ? Math.max(messages.length + start, 0) : Math.min(start, messages.length);
              const to = end < 0 ? Math.max(messages.length + end, 0) : Math.min(end, messages.length);
              const candidates = messages.slice(from, to).map(message => message.content);
              const patterns = Array.isArray(pattern) ? pattern.map(asPattern) : [asPattern(pattern)];
              return candidates.some(content => options.and === true
                ? patterns.every(regex => { regex.lastIndex = 0; return regex.test(content); })
                : patterns.some(regex => { regex.lastIndex = 0; return regex.test(content); }));
            };
            const lodash = value => {
              let current = value;
              return {
                pickBy(predicate) {
                  current = Object.fromEntries(Object.entries(current ?? {}).filter(([key, item]) => predicate(item, key)));
                  return this;
                },
                values() { current = Object.values(current ?? {}); return this; },
                value() { return current; },
              };
            };
            lodash.get = (value, path, fallback) => __eleckoiReadPath(value, path) ?? fallback;
            lodash.values = value => Object.values(value ?? {});
            lodash.pickBy = (value, predicate) => Object.fromEntries(
              Object.entries(value ?? {}).filter(([key, item]) => predicate(item, key)),
            );
            lodash.clamp = (number, lower, upper) => Math.min(Math.max(number, lower), upper);
            lodash.random = (lower = 0, upper = 1) => Math.floor(Math.random() * (upper - lower + 1)) + lower;
            lodash.sample = collection => {
              const values = Array.isArray(collection) ? collection : Object.values(collection ?? {});
              return values.length ? values[Math.floor(Math.random() * values.length)] : undefined;
            };

            const compile = template => {
              const pattern = /<%([_=%\-#]?)([\s\S]*?)([\-_]?%>)/g;
              let cursor = 0;
              let trimNext = '';
              let code = 'let __out = ""; const print = (...values) => { __out += values.map(__toText).join(" "); };';
              let match;
              const appendText = raw => {
                let text = raw;
                if (trimNext === 'all') text = text.replace(/^\s+/, '');
                if (trimNext === 'line') text = text.replace(/^\r?\n/, '');
                trimNext = '';
                if (text) code += `__out += ${ '$' }{JSON.stringify(text)};`;
              };
              while ((match = pattern.exec(template)) !== null) {
                let text = template.slice(cursor, match.index);
                if (match[1] === '_') text = text.replace(/\s+$/, '');
                appendText(text);
                const marker = match[1];
                const body = match[2];
                if (marker === '=' || marker === '-') code += `__out += __toText((${ '$' }{body}));`;
                else if (marker === '%') code += `__out += '<%' + ${ '$' }{JSON.stringify(body)} + '${"%>"}';`;
                else if (marker !== '#') code += body;
                trimNext = match[3].startsWith('_') ? 'all' : match[3].startsWith('-') ? 'line' : '';
                cursor = pattern.lastIndex;
              }
              appendText(template.slice(cursor));
              code += 'return __out;';
              return new AsyncFunction('ctx', '__toText', `with (ctx) { ${ '$' }{code} }`);
            };

            const renderSource = async (source, referenceTrace) => {
              if (!source) return '';
              if (!source.content.includes('<%')) return source.content;
              if (renderStack.includes(source.id)) {
                throw new Error(`getwi 循环引用：${ '$' }{[...renderStack, source.id].join(' -> ')}`);
              }
              renderStack.push(source.id);
              try {
                const getwi = async (...args) => {
                  const requested = [...args].reverse().find(value => typeof value === 'string' && value.trim());
                  if (!requested) return '';
                  const nested = sourceByName.get(`${ '$' }{source.controller_id}\u0000${ '$' }{requested}`);
                  if (!nested) throw new Error(`getwi 找不到条目：${ '$' }{requested}`);
                  referenceTrace.push({ id: nested.id, title: nested.title ?? '', path: nested.path ?? '' });
                  return renderSource(nested, referenceTrace);
                };
                const lastMessageId = input.messages.length ? input.messages[input.messages.length - 1].id : 0;
                const context = {
                  getvar,
                  getwi,
                  matchChatMessages,
                  variables: input.state,
                  stat_data: input.state,
                  _: lodash,
                  lastMessageId,
                  TavernHelper: { getLastMessageId: () => lastMessageId },
                };
                return await compile(source.content)(context, toText);
              } finally {
                renderStack.pop();
              }
            };

            const rendered = {};
            const references = {};
            for (const id of input.target_ids) {
              const source = sourceById.get(id);
              if (!source) throw new Error(`找不到 EJS 目标条目：${ '$' }{id}`);
              const referenceTrace = [];
              rendered[id] = await renderSource(source, referenceTrace);
              references[id] = [...new Map(referenceTrace.map(reference => [reference.id, reference])).values()];
            }
            return JSON.stringify({ rendered, references });
          } catch (error) {
            return JSON.stringify({ error: error && error.message ? error.message : String(error) });
          }
        };
    """.trimIndent()

    val schemaFactory = """
        globalThis.__eleckoiCreateSchema = function(source) {
          const normalized = String(source)
            .replace(/export\s+const\s+Schema\s*=/, 'const Schema =')
            .replace(/export\s+default\s+/, 'const Schema = ');
          let schema;
          try {
            schema = new Function('z', `"use strict"; ${ '$' }{normalized}; return typeof Schema !== "undefined" ? Schema : undefined;`)(globalThis.z);
          } catch (_) {
            schema = undefined;
          }
          if (!schema) {
            schema = new Function('z', `"use strict"; return (${ '$' }{source});`)(globalThis.z);
          }
          return schema;
        };
    """.trimIndent()

    fun schemaProbe(schemaCode: String): String {
        val escapedSchema = JSONObject.quote(schemaCode)
        return """
            (() => {
              const result = { ok: false, message: '', detail: '' };
              try {
                const source = $escapedSchema;
                if (typeof z === 'undefined') {
                  result.message = 'Zod 运行库尚未加载';
                } else {
                  const schema = __eleckoiCreateSchema(source);
                  result.ok = !!schema && typeof schema.safeParse === 'function';
                  result.message = result.ok ? '总校验配置可用' : '总校验配置没有返回 Zod schema';
                }
              } catch (error) {
                result.message = error && error.message ? error.message : String(error);
                result.detail = error && error.stack ? error.stack : '';
              }
              return JSON.stringify(result);
            })()
        """.trimIndent()
    }

    fun stateValidation(schemaCode: String, stateJson: String): String {
        val escapedSchema = JSONObject.quote(schemaCode)
        val escapedState = JSONObject.quote(stateJson)
        return """
            (() => {
              const result = { ok: false, message: '', detail: '' };
              try {
                const schema = __eleckoiCreateSchema($escapedSchema);
                const state = JSON.parse($escapedState);
                const parsed = schema.safeParse(state);
                result.ok = parsed.success === true;
                result.message = parsed.success ? '变量状态校验通过' : '变量状态校验失败';
                if (parsed.success) {
                  result.state = parsed.data;
                }
                if (!parsed.success) {
                  result.detail = JSON.stringify(parsed.error.issues ?? parsed.error, null, 2);
                }
              } catch (error) {
                result.message = error && error.message ? error.message : String(error);
                result.detail = error && error.stack ? error.stack : '';
              }
              return JSON.stringify(result);
            })()
        """.trimIndent()
    }

}
