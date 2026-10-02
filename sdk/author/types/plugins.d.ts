export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
export type Scope = 'message' | 'chat' | 'character' | 'preset' | 'global' | 'script' | 'extension' | 'plugin';
export interface PluginManifest { id: string; name: string; version: string; entry?: string; source: string; enabled?: boolean }
export interface Message { id: string; role: 'system' | 'user' | 'assistant'; content: string; reasoning: string; metadata: Record<string, Json>; swipes: string[]; swipe_id: number }
export interface SqlStatement { sql: string; params?: Json[] }
export interface SqlResult { rows: Record<string, Json>[]; changes: number; lastInsertId: number }
export interface PluginDatabase {
  execute(sql: string, params?: Json[]): Promise<SqlResult>;
  query(sql: string, params?: Json[]): Promise<Record<string, Json>[]>;
  transaction(statements: SqlStatement[]): Promise<SqlResult[]>;
}
export interface GenerationOptions {
  id?: string; conversationId?: string; configId?: string; model?: string; purpose?: string;
  raw?: boolean; stream?: boolean; prompt?: string; responseFormat?: 'json';
  messages?: { role: 'system' | 'user' | 'assistant'; content: string }[];
  parameters?: Record<string, Json>;
}
export interface GenerationResult { id: string; content: string; reasoning: string; model: string }
export interface PromptInjection {
  id: string; content: string; role?: 'system' | 'user' | 'assistant'; order?: number; depth?: number;
  anchor?: 'instructions' | 'beforeToolContext' | 'toolContext' | 'afterToolContext' | 'beforeHistory' | 'afterHistory' | 'beforeLatestUserInput' | 'afterLatestUserInput' | 'beforeToolFlow' | 'afterToolFlow';
  position?: 'in_chat' | 'none'; filter?: () => boolean | Promise<boolean>;
  should_scan?: boolean;
}
export interface UiDescriptor { id: string; label: string; kind: 'panel' | 'settings' | 'message-button' | 'script-button'; html?: string; onClick?: (event: Json) => void | Promise<void> }
export interface ContextOptions { conversationId?: string; characterId?: string; presetId?: string }
export interface GenerationTask { id: string; status: 'running' | 'completed' | 'failed' | 'cancelled'; result?: GenerationResult; error?: string }
export type FileSaveResult = { saved: true; name: string; uri: string; bytes: number } | { saved: false; cancelled: true };
export interface PluginApi {
  ready(): Promise<void>; flush(): Promise<void>;
  call(method: string, params?: Record<string, Json>): Promise<Json>;
  plugins: { list(): Promise<Record<string, PluginManifest>>; install(id: string, manifest: PluginManifest): Promise<boolean>; openManager(): Promise<void>; setEnabled(id: string, enabled: boolean): Promise<boolean>; remove(id: string): Promise<boolean> };
  storage: { kv: { get(key: string): Promise<Json>; set(key: string, value: Json): Promise<void>; delete(key: string): Promise<number>; list(): Promise<Record<string, Json>> }; sqlite: { open(name: string): Promise<PluginDatabase> } };
  settings: { get(): Promise<Record<string, Json>>; set(value: Record<string, Json>): Promise<Record<string, Json>> };
  files: { saveText(options: { name: string; text: string; mimeType?: string }): Promise<FileSaveResult> };
  generation: { invoke(options: GenerationOptions): Promise<GenerationResult>; invokeRaw(options: GenerationOptions): Promise<GenerationResult>; start(options: GenerationOptions): Promise<GenerationTask>; get(id: string): Promise<GenerationTask>; cancel(id: string): Promise<boolean> };
  prompt: { inject(entries: PromptInjection[], options?: { once?: boolean }): { uninject(): void }; remove(ids: string[]): void; preview(): PromptInjection[]; beforeGeneration(callback: (event: { conversationId: string; purpose: string }) => void | Promise<void>): { stop(): void } };
  ui: { register(descriptor: UiDescriptor): Promise<void>; unregister(id: string): Promise<void>; open(id: string): Promise<void>; list(): Promise<UiDescriptor[]>; emit(event: string, payload: Json): Promise<void> };
  variables: { readScope(options: ContextOptions & { scope: Scope; messageId?: string }): Promise<Record<string, Json>>; writeScope(value: Record<string, Json>, options: ContextOptions & { scope: Scope; messageId?: string }): Promise<Record<string, Json>> };
  worldbooks: { list(): Promise<string[]>; get(name: string): Promise<Record<string, Json>>; put(name: string, book: Record<string, Json>): Promise<Record<string, Json>>; delete(name: string): Promise<boolean>; bind(scope: 'global' | 'character' | 'chat', names: string[], options?: ContextOptions): Promise<string[]>; bindings(scope: 'global' | 'character' | 'chat', options?: ContextOptions): Promise<string[]> };
  characters: { list(): Promise<Record<string, Json>[]>; read(options?: ContextOptions): Promise<Record<string, Json>>; write(character: Record<string, Json>, options?: ContextOptions): Promise<Record<string, Json>> };
  personas: { get(): Promise<Record<string, Json>>; set(persona: Record<string, Json>): Promise<Record<string, Json>> };
  presets: { list(): Promise<Record<string, Json>[]>; get(id?: string): Promise<Record<string, Json>>; select(id: string): Promise<Record<string, Json>>; update(id: string, preset: Record<string, Json>): Promise<Record<string, Json>> };
  regex: { get(options?: ContextOptions): Promise<Record<string, Json>>; set(rules: Record<string, Json>, options?: ContextOptions): Promise<Record<string, Json>> };
  macros: { register(name: string, value: string | (() => string)): () => void; unregister(name: string): void; substitute(text: string): string };
  messages: { read(options?: { conversationId?: string }): Promise<Message[]>; update(messages: { id: string; content?: string; expectedContent?: string; metadata?: Record<string, Json> }[], options?: { conversationId?: string }): Promise<Message[]>; insert(messages: Partial<Message>[], options?: { conversationId?: string; index?: number }): Promise<Message[]>; delete(ids: string[], options?: { conversationId?: string }): Promise<Message[]>; metadata(id?: string, value?: Record<string, Json>, options?: { conversationId?: string }): Promise<Record<string, Json>> };
  net: { fetch(url: string, options?: { id?: string; signal?: AbortSignal; method?: string; headers?: Record<string, string>; body?: string; contentType?: string }): Promise<{ status: number; ok: boolean; headers: Record<string, string>; text(): Promise<string>; json(): Promise<Json> }>; cancel(id: string): Promise<boolean> };
  events: { on(name: string, listener: (...args: any[]) => void | Promise<void>): () => void; once(name: string, listener: (...args: any[]) => void | Promise<void>): () => void; off(name: string, listener: (...args: any[]) => void | Promise<void>): void; emit(name: string, ...args: any[]): Promise<void> };
}
