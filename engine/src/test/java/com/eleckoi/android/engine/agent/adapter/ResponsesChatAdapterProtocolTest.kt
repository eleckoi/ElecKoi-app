package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsesChatAdapterProtocolTest {
    @Test
    fun `converts DSH pi-ai easy input messages without outer type`() {
        val request = objectOf(
            """
            {
              "input":[
                {"role":"developer","content":"You are an agent"},
                {"role":"user","content":[{"type":"input_text","text":"检查项目"}]}
              ]
            }
            """.trimIndent(),
        )

        val messages = ResponsesToChatCompletions.convert(request, "kimi-k3").array("messages")

        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject.string("role"))
        assertEquals("You are an agent", messages[0].jsonObject.string("content"))
        assertEquals("user", messages[1].jsonObject.string("role"))
        assertEquals("检查项目", messages[1].jsonObject.string("content"))
    }

    @Test
    fun `keeps separate response text blocks separate in chat messages`() {
        val request = objectOf(
            """{"input":[{"type":"message","role":"developer","content":[{"type":"input_text","text":"第一段"},{"type":"input_text","text":"第二段"}]}]}""",
        )

        val messages = ResponsesToChatCompletions.convert(request, "model").array("messages")

        assertEquals("第一段\n\n第二段", messages.single().jsonObject.string("content"))
    }

    @Test
    fun `converts Responses image input into Chat image_url content`() {
        val request = objectOf(
            """{"input":[{"role":"user","content":[{"type":"input_text","text":"看图"},{"type":"input_image","image_url":"data:image/png;base64,AQID"}]}]}""",
        )

        val messages = ResponsesToChatCompletions.convert(request, "vision-model").array("messages")
        val content = messages.single().jsonObject["content"] as JsonArray

        assertEquals("text", content[0].jsonObject.string("type"))
        assertEquals("看图", content[0].jsonObject.string("text"))
        assertEquals("image_url", content[1].jsonObject.string("type"))
        assertEquals(
            "data:image/png;base64,AQID",
            content[1].jsonObject["image_url"]!!.jsonObject.string("url"),
        )
    }

    @Test
    fun `converts instructions function calls outputs tools and reasoning`() {
        val request = objectOf(
            """
            {
              "model":"ignored",
              "instructions":"You are an Agent Harness",
              "input":[
                {"type":"message","role":"user","content":[{"type":"input_text","text":"检查项目"}]},
                {"type":"reasoning","summary":[],"content":[{"type":"reasoning_text","text":"需要读取文件"}],"encrypted_content":null},
                {"type":"function_call","call_id":"call_1","name":"shell_command","arguments":"{\"command\":\"ls\"}"},
                {"type":"function_call_output","call_id":"call_1","output":"README.md"}
              ],
              "tools":[{"type":"function","name":"shell_command","description":"run","parameters":{"type":"object"},"strict":false}],
              "tool_choice":"auto",
              "parallel_tool_calls":false,
              "stream":true
            }
            """.trimIndent(),
        )

        val chat = ResponsesToChatCompletions.convert(request, "deepseek-test")
        assertEquals("deepseek-test", chat.string("model"))
        val messages = chat["messages"] as JsonArray
        assertEquals("system", messages[0].jsonObject.string("role"))
        val assistant = messages[2].jsonObject
        assertEquals("需要读取文件", assistant.string("reasoning_content"))
        val call = assistant.array("tool_calls")[0].jsonObject
        assertEquals("call_1", call.string("id"))
        assertTrue("index" !in call)
        assertEquals("tool", messages[3].jsonObject.string("role"))
        val function = chat.array("tools")[0].jsonObject["function"]!!.jsonObject
        assertEquals("shell_command", function.string("name"))
        assertTrue("strict" !in function)
        assertTrue("tool_choice" !in chat)
        assertTrue("parallel_tool_calls" !in chat)
        assertTrue("max_tokens" !in chat)
        assertEquals("true", chat["stream_options"]!!.jsonObject["include_usage"].toString())
    }

    @Test
    fun `tool choice none portably omits tools`() {
        val request = objectOf(
            """{"input":[{"type":"message","role":"user","content":"hi"}],"tools":[{"type":"function","name":"read_file","parameters":{"type":"object"}}],"tool_choice":"none"}""",
        )

        val chat = ResponsesToChatCompletions.convert(request, "model")

        assertTrue("tools" !in chat)
        assertTrue("tool_choice" !in chat)
    }

    @Test
    fun `keeps contiguous assistant text and function call in one chat message`() {
        val request = objectOf(
            """
            {
              "input":[
                {"type":"message","role":"user","content":"go"},
                {"type":"message","role":"assistant","content":[{"type":"output_text","text":"我先检查。"}]},
                {"type":"function_call","call_id":"call_1","name":"shell_command","arguments":"{}"}
              ],
              "tools":[{"type":"function","name":"shell_command","parameters":{"type":"object"}}]
            }
            """.trimIndent(),
        )

        val messages = ResponsesToChatCompletions.convert(request, "model").array("messages")
        assertEquals(2, messages.size)
        val assistant = messages[1].jsonObject
        assertEquals("我先检查。", assistant.string("content"))
        assertEquals("call_1", assistant.array("tool_calls")[0].jsonObject.string("id"))
    }

    @Test
    fun `forwards explicit output token limit without a hidden cap`() {
        val request = objectOf(
            """{"input":[{"type":"message","role":"user","content":"go"}],"max_output_tokens":50000}""",
        )

        val chat = ResponsesToChatCompletions.convert(request, "model")

        assertEquals(50_000, (chat["max_tokens"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun `configured output token limit overrides request value`() {
        val request = objectOf(
            """{"input":[{"type":"message","role":"user","content":"go"}],"max_output_tokens":50000}""",
        )

        val chat = ResponsesToChatCompletions.convert(
            request = request,
            upstreamModel = "model",
            configuredMaxOutputTokens = 128_000,
        )

        assertEquals(128_000, (chat["max_tokens"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun `preserves provider reasoning fields without reading Harness effort`() {
        val request = objectOf(
            """
            {
              "input":[{"type":"message","role":"user","content":"go"}],
              "reasoning":{"effort":"medium","summary":"auto"}
            }
            """.trimIndent(),
        )

        val chat = ResponsesToChatCompletions.convert(
            request = request,
            upstreamModel = "deepseek-v4-flash",
            providerRequestFields = buildJsonObject {
                put("thinking", buildJsonObject { put("type", "enabled") })
                put("reasoning_effort", "max")
            },
        )

        assertEquals("enabled", chat["thinking"]!!.jsonObject.string("type"))
        assertEquals("max", chat.string("reasoning_effort"))
    }

    @Test
    fun `does not add provider specific thinking fields by default`() {
        val request = objectOf(
            """{"input":[{"type":"message","role":"user","content":"go"}],"reasoning":{"effort":"high"}}""",
        )

        val chat = ResponsesToChatCompletions.convert(request, "generic-model")

        assertTrue("thinking" !in chat)
        assertTrue("reasoning_effort" !in chat)
    }

    @Test
    fun `wraps responses custom tools as chat functions`() {
        val request = objectOf(
            """
            {
              "input":[
                {"type":"message","role":"user","content":"hi"},
                {"type":"custom_tool_call","call_id":"old_patch","name":"apply_patch","input":"*** Begin Patch\n*** End Patch"},
                {"type":"custom_tool_call_output","call_id":"old_patch","output":"Done"}
              ],
              "tools":[{"type":"custom","name":"apply_patch","description":"edit files","format":{"type":"grammar"}}]
            }
            """.trimIndent(),
        )
        val adapted = ResponsesToChatCompletions.convertWithRoutes(request, "model")
        val function = adapted.body.array("tools")[0].jsonObject["function"]!!.jsonObject
        assertEquals("apply_patch", function.string("name"))
        assertTrue(function.string("description").orEmpty().contains("*** Begin Patch"))
        val inputSchema = function["parameters"]!!.jsonObject["properties"]!!.jsonObject
            .getValue("input").jsonObject
        assertEquals("^\\*\\*\\* Begin Patch", inputSchema.string("pattern"))
        val historyArguments = adapted.body.array("messages")[1].jsonObject
            .array("tool_calls")[0].jsonObject["function"]!!.jsonObject.string("arguments")
        assertTrue(historyArguments.orEmpty().contains("*** Begin Patch"))
        assertEquals(ResponsesToolKind.Custom, adapted.toolRoutes.getValue("apply_patch").kind)
        assertTrue(adapted.priorToolFailures.isEmpty())
    }

    @Test
    fun `captures rejected custom tool result for permanent diagnostics`() {
        val request = objectOf(
            """
            {
              "input":[
                {"type":"message","role":"user","content":"create a file"},
                {"type":"custom_tool_call","call_id":"bad_patch","name":"apply_patch","input":"创建文件"},
                {"type":"custom_tool_call_output","call_id":"bad_patch","output":"apply_patch verification failed: invalid patch: The first line of the patch must be '*** Begin Patch'","success":false}
              ],
              "tools":[{"type":"custom","name":"apply_patch","description":"edit files"}]
            }
            """.trimIndent(),
        )

        val adapted = ResponsesToChatCompletions.convertWithRoutes(request, "model")

        val failure = adapted.priorToolFailures.single()
        assertEquals("bad_patch", failure.callId)
        assertEquals("apply_patch", failure.toolName)
        assertEquals("apply_patch_verification_failed", failure.code)
        assertTrue(failure.message.contains("first line", ignoreCase = true))
        assertTrue(failure.outputChars > 0)
        val toolMessage = adapted.body.array("messages").last().jsonObject
        assertEquals("tool", toolMessage.string("role"))
        assertEquals("bad_patch", toolMessage.string("tool_call_id"))
        assertTrue(toolMessage.string("content").orEmpty().startsWith("apply_patch verification failed:"))
    }

    @Test
    fun `does not replay an old tool failure into a later user turn`() {
        val request = objectOf(
            """
            {
              "input":[
                {"type":"message","role":"user","content":"old turn"},
                {"type":"custom_tool_call","call_id":"old_bad_patch","name":"apply_patch","input":"bad"},
                {"type":"custom_tool_call_output","call_id":"old_bad_patch","output":"apply_patch verification failed: invalid patch"},
                {"type":"message","role":"assistant","content":"fallback completed"},
                {"type":"message","role":"user","content":"new turn"}
              ],
              "tools":[{"type":"custom","name":"apply_patch","description":"edit files"}]
            }
            """.trimIndent(),
        )

        val adapted = ResponsesToChatCompletions.convertWithRoutes(request, "model")

        assertTrue(adapted.priorToolFailures.isEmpty())
    }

    @Test
    fun `omits hosted web search while retaining local coding tools`() {
        val request = objectOf(
            """
            {
              "input":[{"type":"message","role":"user","content":"hi"}],
              "tools":[
                {"type":"web_search"},
                {"type":"function","name":"read_file","parameters":{"type":"object"}}
              ],
              "tool_choice":"auto"
            }
            """.trimIndent(),
        )

        val chat = ResponsesToChatCompletions.convert(request, "model")

        assertEquals(1, chat.array("tools").size)
        assertEquals(
            "read_file",
            chat.array("tools")[0].jsonObject["function"]!!.jsonObject.string("name"),
        )
    }

    @Test
    fun `flattens namespace tools and restores namespace on streamed calls`() {
        val request = objectOf(
            """
            {
              "input":[
                {"type":"message","role":"user","content":"检查项目"},
                {"type":"function_call","call_id":"old_call","namespace":"functions","name":"shell_command","arguments":"{\"command\":\"pwd\"}"},
                {"type":"function_call_output","call_id":"old_call","output":"/workspace"}
              ],
              "tools":[{
                "type":"namespace",
                "name":"functions",
                "description":"Local coding tools",
                "tools":[{"type":"function","name":"shell_command","description":"run","parameters":{"type":"object"}}]
              }]
            }
            """.trimIndent(),
        )

        val adapted = ResponsesToChatCompletions.convertWithRoutes(request, "model")
        val chatTool = adapted.body.array("tools")[0].jsonObject["function"]!!.jsonObject
        assertEquals("functions__shell_command", chatTool.string("name"))
        val historyCall = adapted.body.array("messages")[1].jsonObject
            .array("tool_calls")[0].jsonObject["function"]!!.jsonObject
        assertEquals("functions__shell_command", historyCall.string("name"))

        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "namespace" },
            toolRoutes = adapted.toolRoutes,
        )
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"new_call","function":{"name":"functions__shell_","arguments":"{\"command\":"}}]},"finish_reason":null}]}""",
        )
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"command","arguments":"\"ls\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )
        val call = stream.acceptData("[DONE]")
            .first { it.payload["item"]?.jsonObject?.string("type") == "function_call" }
            .payload["item"]!!.jsonObject
        assertEquals("functions", call.string("namespace"))
        assertEquals("shell_command", call.string("name"))
        assertEquals("{\"command\":\"ls\"}", call.string("arguments"))
    }

    @Test
    fun `streams text and fragmented tool calls into responses events`() {
        var next = 0
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "id${next++}" },
            toolRoutes = functionRoutes("shell_command"),
        )
        val first = stream.acceptData(
            """{"id":"chatcmpl_1","model":"deepseek","choices":[{"index":0,"delta":{"reasoning_content":"先看"},"finish_reason":null}]}""",
        )
        assertEquals("response.created", first[0].type)
        assertEquals("response.output_item.added", first[1].type)
        assertEquals("response.reasoning_text.delta", first[2].type)
        val startedReasoningId = first[1].payload["item"]!!.jsonObject.string("id")
        assertEquals(startedReasoningId, first[2].payload.string("item_id"))
        val second = stream.acceptData(
            """{"id":"chatcmpl_1","model":"deepseek","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"shell_","arguments":"{\"command\":"}}]},"finish_reason":null}]}""",
        )
        assertTrue(second.isEmpty())
        val toolStarted = stream.acceptData(
            """{"id":"chatcmpl_1","model":"deepseek","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"command","arguments":"\"ls\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )
        val done = stream.acceptData("[DONE]")
        val allEvents = first + toolStarted + done
        val reasoning = allEvents.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "reasoning"
        }
        assertEquals("先看", reasoning.payload["item"]!!.jsonObject.array("content")[0].jsonObject.string("text"))
        val addedCall = toolStarted.first {
            it.type == "response.output_item.added" &&
                it.payload["item"]?.jsonObject?.string("type") == "function_call"
        }.payload["item"]!!.jsonObject
        assertEquals("in_progress", addedCall.string("status"))
        val argumentDelta = toolStarted.first { it.type == "response.function_call_arguments.delta" }
        assertEquals("{\"command\":\"ls\"}", argumentDelta.payload.string("delta"))
        val call = done.first { it.payload["item"]?.jsonObject?.string("type") == "function_call" }
        val item = call.payload["item"]!!.jsonObject
        assertEquals(addedCall.string("id"), item.string("id"))
        assertEquals(addedCall.string("call_id"), item.string("call_id"))
        assertEquals("shell_command", item.string("name"))
        assertEquals("{\"command\":\"ls\"}", item.string("arguments"))
        assertEquals("response.completed", done.last().type)
    }

    @Test
    fun `streams wrapped chat arguments as native custom tool input deltas`() {
        val request = objectOf(
            """{"input":[{"type":"message","role":"user","content":"edit"}],"tools":[{"type":"custom","name":"apply_patch","description":"edit"}]}""",
        )
        val adapted = ResponsesToChatCompletions.convertWithRoutes(request, "model")
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "stream" },
            toolRoutes = adapted.toolRoutes,
        )

        val first = stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_patch","function":{"name":"apply_","arguments":"{\"inp"}}]},"finish_reason":null}]}""",
        )
        val second = stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"patch","arguments":"ut\":\"*** Begin Patch\\n*** Add File: live.txt\\n+live"}}]},"finish_reason":null}]}""",
        )
        val third = stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":" line\\n*** End Patch\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )

        assertEquals(listOf("response.created"), first.map(ResponsesSseEvent::type))
        val added = second.first { it.type == "response.output_item.added" }
            .payload["item"]!!.jsonObject
        assertEquals("custom_tool_call", added.string("type"))
        assertEquals("apply_patch", added.string("name"))
        assertEquals("call_patch", added.string("call_id"))
        assertEquals("in_progress", added.string("status"))
        assertEquals("", added.string("input"))
        val streamedPatch = (second + third)
            .filter { it.type == "response.custom_tool_call_input.delta" }
            .joinToString(separator = "") { it.payload.string("delta").orEmpty() }
        assertEquals(
            "*** Begin Patch\n*** Add File: live.txt\n+live line\n*** End Patch",
            streamedPatch,
        )

        val done = stream.acceptData("[DONE]")
        val completed = done.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "custom_tool_call"
        }.payload["item"]!!.jsonObject
        assertEquals(added.string("id"), completed.string("id"))
        assertEquals(added.string("call_id"), completed.string("call_id"))
        assertEquals(streamedPatch, completed.string("input"))
        assertEquals("response.completed", done.last().type)
    }

    @Test
    fun `streams reasoning and commentary with official item lifecycle`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "lifecycle" },
            toolRoutes = functionRoutes("apply_patch"),
        )
        val reasoning = stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"reasoning_content":"先分析"},"finish_reason":null}]}""",
        )
        val commentary = stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"content":"我开始修改","tool_calls":[{"index":0,"id":"call_1","function":{"name":"apply_patch","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
        )

        assertEquals(
            listOf("response.created", "response.output_item.added", "response.reasoning_text.delta"),
            reasoning.map(ResponsesSseEvent::type),
        )
        assertEquals(
            listOf(
                "response.output_item.done",
                "response.output_item.added",
                "response.output_text.delta",
                "response.output_item.done",
                "response.output_item.added",
                "response.function_call_arguments.delta",
            ),
            commentary.map(ResponsesSseEvent::type),
        )
        val reasoningId = reasoning[1].payload["item"]!!.jsonObject.string("id")
        assertEquals(reasoningId, commentary[0].payload["item"]!!.jsonObject.string("id"))
        val messageId = commentary[1].payload["item"]!!.jsonObject.string("id")
        assertEquals(messageId, commentary[2].payload.string("item_id"))

        val completedMessage = commentary.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]!!.jsonObject.string("type") == "message"
        }.payload["item"]!!.jsonObject
        assertEquals(messageId, completedMessage.string("id"))
        assertEquals("commentary", completedMessage.string("phase"))
        val done = stream.acceptData("[DONE]")
        assertTrue(done.any { it.payload["item"]?.jsonObject?.string("type") == "function_call" })
        assertEquals("response.completed", done.last().type)
    }

    @Test
    fun `extracts fragmented inline think content before a tool call`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "minimax" },
            toolRoutes = functionRoutes("eleckoi_capability_probe"),
        )
        val events = buildList {
            addAll(stream.acceptData(
                """{"id":"c","choices":[{"index":0,"delta":{"content":"<thi"},"finish_reason":null}]}""",
            ))
            addAll(stream.acceptData(
                """{"id":"c","choices":[{"index":0,"delta":{"content":"nk>checking</thi"},"finish_reason":null}]}""",
            ))
            addAll(stream.acceptData(
                """{"id":"c","choices":[{"index":0,"delta":{"content":"nk>\n\n","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"eleckoi_capability_probe","arguments":"{\"value\":\"ok\"}"}}]},"finish_reason":"tool_calls"}]}""",
            ))
            addAll(stream.acceptData("[DONE]"))
        }

        assertEquals(
            "checking",
            events.filter { it.type == "response.reasoning_text.delta" }
                .joinToString("") { it.payload.string("delta").orEmpty() },
        )
        assertTrue(events.none {
            it.type == "response.output_text.delta" &&
                it.payload.string("delta").orEmpty().contains("<think>")
        })
        assertTrue(events.any {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "function_call"
        })
    }

    @Test
    fun `marks tool round text as commentary and terminal text as final answer`() {
        val toolRound = ChatCompletionsToResponsesStream(
            idFactory = { "tool" },
            toolRoutes = functionRoutes("shell_command"),
        )
        val started = toolRound.acceptData(
            """{"id":"tool-round","choices":[{"index":0,"delta":{"content":"我先检查项目","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"shell_command","arguments":"{\"command\":\"ls\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )
        val commentary = (started + toolRound.acceptData("[DONE]"))
            .first {
                it.type == "response.output_item.done" &&
                    it.payload["item"]?.jsonObject?.string("type") == "message"
            }
            .payload["item"]!!.jsonObject
        assertEquals("commentary", commentary.string("phase"))

        val terminalRound = ChatCompletionsToResponsesStream(idFactory = { "final" })
        terminalRound.acceptData(
            """{"id":"final-round","choices":[{"index":0,"delta":{"content":"已经完成"},"finish_reason":"stop"}]}""",
        )
        val finalAnswer = terminalRound.acceptData("[DONE]")
            .first { it.payload["item"]?.jsonObject?.string("type") == "message" }
            .payload["item"]!!.jsonObject
        assertEquals("final_answer", finalAnswer.string("phase"))
    }

    @Test
    fun `detects a fragmented final phase header while retaining it in the current turn`() {
        val stream = ChatCompletionsToResponsesStream(idFactory = { "phase-final" })

        val first = stream.acceptData(
            """{"id":"phase","choices":[{"index":0,"delta":{"content":"<FI"},"finish_reason":null}]}""",
        )
        val second = stream.acceptData(
            """{"id":"phase","choices":[{"index":0,"delta":{"content":"NA"},"finish_reason":null}]}""",
        )
        val third = stream.acceptData(
            """{"id":"phase","choices":[{"index":0,"delta":{"content":"L>\r"},"finish_reason":null}]}""",
        )
        val fourth = stream.acceptData(
            """{"id":"phase","choices":[{"index":0,"delta":{"content":"\n最终回复\n</FINAL>"},"finish_reason":"stop"}]}""",
        )

        assertEquals(listOf("response.created"), first.map(ResponsesSseEvent::type))
        assertTrue(second.isEmpty())
        assertEquals(
            listOf("response.output_item.added", "response.output_text.delta"),
            third.map(ResponsesSseEvent::type),
        )
        assertEquals("final_answer", third[0].payload["item"]!!.jsonObject.string("phase"))
        assertEquals("<FINAL>\r", third[1].payload.string("delta"))
        assertEquals(listOf("response.output_text.delta"), fourth.map(ResponsesSseEvent::type))
        assertEquals("\n最终回复\n</FINAL>", fourth[0].payload.string("delta"))

        val done = stream.acceptData("[DONE]")
        val message = done.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "message"
        }.payload["item"]!!.jsonObject
        assertEquals("final_answer", message.string("phase"))
        assertEquals(
            "<FINAL>\r\n最终回复\n</FINAL>",
            message.array("content")[0].jsonObject.string("text"),
        )
    }

    @Test
    fun `keeps a commentary phase for the message that precedes a tool call`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "phase-commentary" },
            toolRoutes = functionRoutes("shell_command"),
        )

        val events = stream.acceptData(
            """{"id":"phase","choices":[{"index":0,"delta":{"content":"<COMMENTARY>\n我先检查\n</COMMENTARY>","tool_calls":[{"index":0,"id":"call_1","function":{"name":"shell_command","arguments":"{\"command\":\"ls\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )

        val addedMessage = events.first {
            it.type == "response.output_item.added" &&
                it.payload["item"]?.jsonObject?.string("type") == "message"
        }.payload["item"]!!.jsonObject
        val completedMessage = events.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "message"
        }.payload["item"]!!.jsonObject
        assertEquals("commentary", addedMessage.string("phase"))
        assertEquals("commentary", completedMessage.string("phase"))
        assertEquals(
            "<COMMENTARY>\n我先检查\n</COMMENTARY>",
            completedMessage.array("content")[0].jsonObject.string("text"),
        )
        assertEquals(
            "<COMMENTARY>\n我先检查\n</COMMENTARY>",
            events.filter { it.type == "response.output_text.delta" }
                .joinToString(separator = "") { it.payload.string("delta").orEmpty() },
        )
    }

    @Test
    fun `releases an incomplete phase prefix unchanged when the stream ends`() {
        val stream = ChatCompletionsToResponsesStream(idFactory = { "phase-incomplete" })
        val started = stream.acceptData(
            """{"id":"phase","choices":[{"index":0,"delta":{"content":"<FI"},"finish_reason":"stop"}]}""",
        )

        assertEquals(listOf("response.created"), started.map(ResponsesSseEvent::type))
        val done = stream.acceptData("[DONE]")
        assertEquals(
            "<FI",
            done.first { it.type == "response.output_text.delta" }.payload.string("delta"),
        )
        val message = done.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "message"
        }.payload["item"]!!.jsonObject
        assertEquals("<FI", message.array("content")[0].jsonObject.string("text"))
    }

    @Test
    fun `normalizes common chat reasoning fields into responses events`() {
        val payloads = listOf(
            "\"reasoning\":\"reasoning text\"" to "reasoning text",
            "\"analysis\":\"analysis text\"" to "analysis text",
            "\"thinking\":\"thinking text\"" to "thinking text",
            "\"reasoning_details\":[{\"text\":\"first \"},{\"content\":\"second\"}]" to "first second",
        )

        payloads.forEachIndexed { index, (field, expected) ->
            val stream = ChatCompletionsToResponsesStream(idFactory = { "reasoning$index" })
            val first = stream.acceptData(
                """{"id":"c$index","choices":[{"index":0,"delta":{$field},"finish_reason":null}]}""",
            )
            assertEquals("response.reasoning_text.delta", first.last().type)
            assertEquals(expected, first.last().payload.string("delta"))

            val done = stream.acceptData("[DONE]")
            val reasoning = done.first { it.payload["item"]?.jsonObject?.string("type") == "reasoning" }
            assertEquals(
                expected,
                reasoning.payload["item"]!!.jsonObject.array("content")[0].jsonObject.string("text"),
            )
        }
    }

    @Test
    fun `replay buffer batches character sized reasoning fragments without changing their text`() {
        val stream = ChatCompletionsToResponsesStream(idFactory = { "batched-reasoning" })
        val replayBuffer = ResponsesEventReplayBuffer(nanoTime = { 0L })
        val events = buildList {
            repeat(700) {
                addAll(
                    replayBuffer.consume(
                        stream.acceptData(
                            """{"id":"c","choices":[{"index":0,"delta":{"reasoning_content":"字"},"finish_reason":null}]}""",
                        ),
                    ),
                )
            }
            addAll(replayBuffer.consume(stream.acceptData("[DONE]")))
            addAll(replayBuffer.flush())
        }

        val deltas = events.filter { it.type == "response.reasoning_text.delta" }
        assertTrue("逐字流不应继续产生数百个 Harness 事件", deltas.size < 30)
        assertEquals("字".repeat(700), deltas.joinToString("") { it.payload.string("delta").orEmpty() })
        val completedReasoning = events.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "reasoning"
        }.payload["item"]!!.jsonObject
        assertEquals(
            "字".repeat(700),
            completedReasoning.array("content")[0].jsonObject.string("text"),
        )
    }

    @Test
    fun `promotes reasoning into final answer only when terminal body is empty`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "reasoning-fallback" },
            allowTerminalReasoningFallback = true,
        )
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"reasoning_content":"你好，我是测试角色。"},"finish_reason":"stop"}]}""",
        )

        val events = stream.acceptData("[DONE]")
        val finalAnswer = events.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "message"
        }.payload["item"]!!.jsonObject

        assertEquals("final_answer", finalAnswer.string("phase"))
        assertEquals(
            "你好，我是测试角色。",
            finalAnswer.array("content")[0].jsonObject.string("text"),
        )
        assertTrue(events.any {
            it.type == "response.output_text.delta" &&
                it.payload.string("delta") == "你好，我是测试角色。"
        })
    }

    @Test
    fun `does not promote reasoning for later provider requests`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "later-request" },
            allowTerminalReasoningFallback = false,
        )
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"reasoning_content":"这里只是中间请求的思考"},"finish_reason":"stop"}]}""",
        )

        val events = stream.acceptData("[DONE]")
        val finalAnswer = events.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "message"
        }.payload["item"]!!.jsonObject

        assertEquals("", finalAnswer.array("content")[0].jsonObject.string("text"))
        assertTrue(events.none { it.type == "response.output_text.delta" })
    }

    @Test
    fun `does not promote reasoning when the request calls a tool`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "tool-request" },
            toolRoutes = functionRoutes("shell_command"),
            allowTerminalReasoningFallback = true,
        )
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"reasoning_content":"先读取文件","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"shell_command","arguments":"{\"command\":\"ls\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )

        val events = stream.acceptData("[DONE]")

        assertTrue(events.none { it.type == "response.output_text.delta" })
        assertTrue(events.any {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "function_call"
        })
    }

    @Test
    fun `does not promote reasoning when a normal final body exists`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "normal-answer" },
            allowTerminalReasoningFallback = true,
        )
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"reasoning_content":"内部思考"},"finish_reason":null}]}""",
        )
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"content":"正常正文"},"finish_reason":"stop"}]}""",
        )

        val events = stream.acceptData("[DONE]")
        val finalAnswer = events.first {
            it.type == "response.output_item.done" &&
                it.payload["item"]?.jsonObject?.string("type") == "message"
        }.payload["item"]!!.jsonObject

        assertEquals("正常正文", finalAnswer.array("content")[0].jsonObject.string("text"))
        assertTrue(events.none {
            it.type == "response.output_text.delta" &&
                it.payload.string("delta") == "内部思考"
        })
    }

    @Test
    fun `text length finish requests an automatic continuation instead of context failure`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "x" },
            estimatedInputTokens = 123,
        )
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"content":"partial"},"finish_reason":"length"}]}""",
        )
        val events = stream.acceptData("[DONE]")
        assertTrue(events.none { it.type == "response.failed" })
        val completed = events.last()
        assertEquals("response.completed", completed.type)
        val response = completed.payload["response"]!!.jsonObject
        assertEquals("false", response["end_turn"].toString())
        val partialMessage = events.first {
            it.payload["item"]?.jsonObject?.string("type") == "message"
        }.payload["item"]!!.jsonObject
        assertTrue("phase" !in partialMessage)
        val usage = response["usage"]!!.jsonObject
        assertEquals("123", usage["input_tokens"].toString())
        assertTrue((usage["output_tokens"] as JsonPrimitive).content.toInt() > 0)
    }

    @Test
    fun `truncated tool call is incomplete and is never executed`() {
        val stream = ChatCompletionsToResponsesStream(idFactory = { "x" })
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"shell_command","arguments":"{\\\"command\\\":"}}]},"finish_reason":"length"}]}""",
        )

        val events = stream.acceptData("[DONE]")

        assertTrue(events.none { event ->
            event.payload["item"]?.jsonObject?.string("type") == "function_call"
        })
        val incomplete = events.last()
        assertEquals("response.incomplete", incomplete.type)
        assertEquals(
            "max_output_tokens",
            incomplete.payload["response"]!!.jsonObject["incomplete_details"]!!
                .jsonObject.string("reason"),
        )
    }

    @Test
    fun `restores chat wrapper call as native custom tool call`() {
        val request = objectOf(
            """{"input":[{"type":"message","role":"user","content":"edit"}],"tools":[{"type":"custom","name":"apply_patch","description":"edit"}]}""",
        )
        val adapted = ResponsesToChatCompletions.convertWithRoutes(request, "model")
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "custom" },
            toolRoutes = adapted.toolRoutes,
        )
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_patch","function":{"name":"apply_patch","arguments":"{\"input\":\"*** Begin Patch\\n*** End Patch\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )

        val events = stream.acceptData("[DONE]")
        val item = events.first {
            it.payload["item"]?.jsonObject?.string("type") == "custom_tool_call"
        }.payload["item"]!!.jsonObject
        assertEquals("apply_patch", item.string("name"))
        assertEquals("*** Begin Patch\n*** End Patch", item.string("input"))
        assertTrue("arguments" !in item)
        assertEquals(null, stream.terminalFailure())
    }

    @Test
    fun `forwards an undeclared function call for DSH to return its error to the model`() {
        val stream = ChatCompletionsToResponsesStream(idFactory = { "route" })
        stream.acceptData(
            """{"id":"c","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"apply_patch","arguments":"{\"input\":\"hidden patch\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )

        val events = stream.acceptData("[DONE]")

        assertEquals("response.completed", events.last().type)
        assertEquals(null, stream.terminalFailure())
        val item = events.first {
            it.type == "response.output_item.done" &&
            it.payload["item"]?.jsonObject?.string("type") == "function_call"
        }.payload["item"]!!.jsonObject
        assertEquals("apply_patch", item.string("name"))
        assertEquals("call_1", item.string("call_id"))
        assertEquals("""{"input":"hidden patch"}""", item.string("arguments"))
    }

    @Test
    fun `uses upstream stream usage when the provider sends it`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "usage" },
            estimatedInputTokens = 1,
        )
        stream.acceptData(
            """{"id":"c","choices":[],"usage":{"prompt_tokens":900,"completion_tokens":100,"total_tokens":1000}}""",
        )

        val events = stream.acceptData("[DONE]")
        val usage = events.last().payload["response"]!!.jsonObject["usage"]!!.jsonObject
        assertEquals("900", usage["input_tokens"].toString())
        assertEquals("100", usage["output_tokens"].toString())
        assertEquals("1000", usage["total_tokens"].toString())
    }

    @Test
    fun `maps DeepSeek prompt cache hits into Responses input token details`() {
        val stream = ChatCompletionsToResponsesStream(
            idFactory = { "deepseek-cache-usage" },
            estimatedInputTokens = 1,
        )
        stream.acceptData(
            """{"id":"c","choices":[],"usage":{"prompt_tokens":283,"completion_tokens":69,"total_tokens":352,"prompt_cache_hit_tokens":256,"prompt_cache_miss_tokens":27}}""",
        )

        val events = stream.acceptData("[DONE]")
        val usage = events.last().payload["response"]!!.jsonObject["usage"]!!.jsonObject
        assertEquals("283", usage["input_tokens"].toString())
        assertEquals(
            "256",
            usage["input_tokens_details"]!!.jsonObject["cached_tokens"].toString(),
        )
        assertEquals("69", usage["output_tokens"].toString())
        assertEquals("352", usage["total_tokens"].toString())
    }

    @Test
    fun `prefers OpenAI compatible cached token details when both spellings are present`() {
        val stream = ChatCompletionsToResponsesStream(idFactory = { "compatible-cache-usage" })
        stream.acceptData(
            """{"id":"c","choices":[],"usage":{"prompt_tokens":300,"completion_tokens":20,"total_tokens":320,"prompt_cache_hit_tokens":128,"prompt_tokens_details":{"cached_tokens":256}}}""",
        )

        val events = stream.acceptData("[DONE]")
        val usage = events.last().payload["response"]!!.jsonObject["usage"]!!.jsonObject
        assertEquals(
            "256",
            usage["input_tokens_details"]!!.jsonObject["cached_tokens"].toString(),
        )
    }

    private fun objectOf(json: String) = ElecKoiJson.parseToJsonElement(json).jsonObject
    private fun functionRoutes(vararg names: String): Map<String, ResponsesToolRoute> = names.associateWith { name ->
        ResponsesToolRoute(
            chatName = name,
            namespace = null,
            responseName = name,
            kind = ResponsesToolKind.Function,
        )
    }
    private fun JsonObject.string(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.array(name: String): JsonArray = get(name) as JsonArray
}
