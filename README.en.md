<p align="center">
  <img src="app/src/main/assets/model-icons/whale-maid-thinking.png" width="128" alt="ElecKoi thinking whale-maid mascot">
</p>

<h1 align="center">ElecKoi</h1>

<p align="center">
  <a href="./README.md">简体中文</a> |
  <strong>English</strong>
</p>

ElecKoi is an AI character-card creation and roleplay client powered by DSH Agent. It will first provide a free and open-source Android client, followed by a PC client.

## Highlights

**Create characters the way you vibe code.** Describe what you want, then let Agents and tools handle the tedious parts of character-card authoring step by step.

- **Character Agents that consult their own lore**: DSH gives characters real Agent capabilities. They can proactively search lorebooks, read only what the current scene needs, and continue the roleplay by using variables, network access, and other tools.
- **From manual card authoring to Agent-driven creation**: Creation Agents can build and edit characters, lorebooks, variables, regex rules, and visual assets. Every change is previewed and validated before the creator decides whether to save it.
- **Reliable variable updates through tool calls**: The Agent does not emit a prescribed variable-update format in its replies. Instead, it uses dedicated tools to read, validate, and update relationships, items, events, and world state. Each tool has a defined data schema and operating rules, reducing output drift and making state changes more reliable and traceable. Characters can also include their own HTML, CSS, JavaScript, and assets to present that state through an interactive roleplay interface.

ElecKoi aims to build a continuously improving creative flywheel: turn the community's accumulated character-design knowledge, templates, and methods into capabilities that Agents can retrieve and reuse. As that knowledge grows, Agents become better creators; the resulting works and experience can then help the whole community move forward.

## App Screenshots

<table>
  <tr>
    <td align="center" valign="top">
      <img src="docs/screenshots/roleplay-chat.png" alt="Roleplay conversation screen" width="260">
      <br><sub>Roleplay conversation</sub>
    </td>
    <td align="center" valign="top">
      <img src="docs/screenshots/agent-details.png" alt="Agent execution details screen" width="260">
      <br><sub>Agent execution details</sub>
    </td>
  </tr>
</table>

## Upcoming Development Goals

> This section lists future work and does not indicate that these features are already implemented.

- [ ] Support prompt templates and frontend character-card startup flows, so clicking Start can initiate the first AI turn using the intended template.
- [ ] Expand the frontend API documentation and examples so Agents can design frontend styles and automatically load character cards.
- [ ] Improve DSH plugin management and MCP integration with unified configuration, permissions, and runtime status.
- [ ] Support the official APIs of more model providers.
- [ ] Develop the ElecKoi PC client and gradually bring character creation and roleplay to more platforms.
- [ ] Improve image-generation capabilities, including model integrations, parameter controls, editing, and iterative creation workflows.

## Current Development Challenges

Open problems that require focused attention will be collected in [Open Problems in AI Roleplay Development](docs/open-problems/README.en.md).

## QQ Group

`1041463229`

## Why ElecKoi

ElecKoi is more than another AI chat app.

I hope "AI roleplay designer" can become a genuine emerging profession: one that is seen, respected, and capable of providing lawful income. Great character works deserve to be created with care and remembered like novels, games, and films, while their creators deserve the chance to keep going through their own passion and ability.

ElecKoi's ultimate goal is to accelerate the birth and adoption of this profession. We will begin with free and open-source clients, Agent-powered creation tools, richer ways to present character works, and an ecosystem that supports creators, laying this road one step at a time. However long that future takes to arrive, ElecKoi will take the first step and keep moving toward it.

What we want to offer everyone who loves AI roleplay is more than better software. We want to help open a path that did not exist before: at a time when employment is challenging, people who love characters and have a gift for creating them should have one more future worth seriously choosing.

ElecKoi explores more than AI character conversations. It also covers AI-driven games, interactive storytelling, and creative forms that have yet to be defined. Some frontend-heavy character cards already show this potential: a character experience no longer needs to be a simple conversation. It can become a beautifully presented simulation game where people live through all kinds of interesting stories, or even turn a world that once existed only in the imagination into an open world they can explore freely. ElecKoi wants to work with creators to develop these early forms into richer works.

ElecKoi aims to provide an open toolset for this work over the long term and to bring together people who believe in the same goal. One person can only do so much. If the project earns revenue in the future, I hope to invite capable developers, designers, and creators to join, compensate them fairly, and build the project better and for longer.

I promise that ElecKoi's official Android client and future PC client will remain free and open source and will not become closed-source products. Official clients will not include paid features or recommend any API relay service. Users choose and configure the model providers they trust.

If the project gains sustainable revenue, the current direction is to build a resource and services platform for the AI character-creation ecosystem. It would help creators showcase, publish, and trade character works, original-character commissions, art assets, interface designs, plugins, extensions, and other creative content and services, while continuing to maintain their work. The platform may eventually earn revenue through transparent service fees, sponsorships, or donations. That revenue would support infrastructure, compensate collaborators, build a professional team, and fund long-term maintenance. Specific rules will be published before any related service launches.

I do not believe open source should require developers and creators to go unrewarded. My hope is for ElecKoi to form a healthy cycle: the core tools remain freely available, creators decide how their original work is licensed and priced, and the project gains enough resources to continue developing.

## Content Boundaries

ElecKoi is an open-source client that runs locally. The project does not provide model services and has no backend that receives users' local character cards, lore, or chat records. It cannot and will not access that private data. Users choose the model services they connect to.

If a design-resource and services platform launches in the future, it will only support lawful and compliant design resources and creative services, and publicly published or traded content on the platform will be managed in accordance with applicable law.

## Ecosystem Compatibility and Acknowledgements

Thank you to the following open-source projects and creators for advancing the AI Agent and roleplay ecosystems:

- Special thanks to [DeepSeek Harness (DSH)](https://github.com/deepseek-ai/deepseek-harness) for providing the Agent execution framework. ElecKoi's Agent runtime is based on DSH and connects it to character sessions, creation tools, permission approval, subagents, and the Android runtime environment. DSH and its upstream code remain subject to the MIT License and their respective third-party licenses.
- Thanks to [SillyTavern](https://github.com/SillyTavern/SillyTavern) for advancing the ecosystem around character cards, presets, lorebooks, regex rules, and rich-content creation. ElecKoi selectively supports some of its content formats and authoring conventions, but does not aim to reproduce its frontend, extension runtime, or complete behavioral system.
- Thanks to [Tavern-Helper](https://github.com/N0VI028/JS-Slash-Runner) and the MVU content ecosystem for advancing variable-driven roleplay. ElecKoi carries forward the idea that state can participate in storytelling, implemented through its own hierarchical data model, Agent tools, patch protocol, Zod validation, message snapshots, and transactional commits.
- Thanks to [TauriTavern](https://github.com/Darkatse/TauriTavern) for publicly exploring long-conversation and rich-content runtime challenges on mobile, providing useful engineering practice for comparison during ElecKoi's research.
- Thanks to [EJS](https://github.com/mde/ejs) for establishing its template syntax and creative ecosystem. ElecKoi independently implements compatibility with part of the EJS template syntax and does not bundle the upstream EJS runtime.

For the authoritative record of third-party code, binaries, assets, and licenses actually distributed with the app, refer to `NOTICE` and the license files throughout the repository.

## Who I Hope to Work With

I welcome developers, AI roleplay designers, visual and interaction designers, documentation writers, and community builders who share the following principles:

- The core tools should remain free and open source.
- Original authors, compatible ecosystems, and the factual history of the technology should be respected.
- Character designers have the right to choose licenses for their original work and to earn lawful income from it.
- AI roleplay should remain lawful and compliant; together, we should uphold the legal line and avoid infringing the rights of others.
- Open source, creator rights, and sustainable maintenance should reinforce one another over the long term.

If you also want AI character-card design to become a fuller, more compliant, and more sustainable form of creative work, you are welcome to participate through Issues, Discussions, or Pull Requests.

## Project Direction and Contribution Terms

ElecKoi's official Android client and future PC client will remain free and open source. The project may eventually be connected to an independent resource and services platform for AI character creation. That platform would support long-term development and fair compensation for collaborators through services, sponsorships, or donations; fulfilling those commitments is an obligation and responsibility.

### Shared Understanding of the Project Direction

Long-term collaboration on ElecKoi means recognizing the basic development direction already stated publicly in this README: the core client will remain free and open source; the rights of open-source projects, original authors, and creators will be respected; the AI character-creation ecosystem will be developed lawfully and responsibly; and the project will work toward a sustainable, mutually reinforcing balance among open collaboration, creator income, and long-term maintenance.

Contributing does not mean that contributors give up copyright in their code or works, nor does it require unconditional acceptance of every future change in direction. If the project makes a substantive change to its open-source model, business model, boundaries around user data, creator rights, or other major principles, it should disclose that change publicly as early as reasonably possible and communicate with contributors, so that current and future participants can make an informed decision about whether to continue collaborating.

Contributors retain copyright in their contributions, while the code continues to be provided under `AGPL-3.0-or-later`. An ordinary Issue or Pull Request does not transfer copyright and does not grant the project the right to relicense a contribution as closed-source code. A future resource website or platform service would likewise gain no additional rights to contributor code, works, or other content.

If you have questions about this direction, please discuss them through an Issue or Discussion before contributing. I do not want anyone to discover only after contributing their work that the project's direction conflicts with their values.

## Contributors

Thank you to everyone who helps make ElecKoi better.

[![ElecKoi contributors](https://contrib.rocks/image?repo=eleckoi/ElecKoi-app)](https://github.com/eleckoi/ElecKoi-app/graphs/contributors)

## Building

```powershell
.\gradlew.bat :app:assembleDebug
```

The repository includes the Gradle Wrapper, Android modules, native code, WebView runtime assets, and the files required to build the local Runtime. Large `runtime/bundles/*.egruntime` files are excluded from version control and prepared automatically from a pinned manifest during the build.

## Open-source License

Except for third-party components identified separately, ElecKoi's source code is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE) (`AGPL-3.0-or-later`). Third-party components remain under their respective licenses; see `NOTICE` and the license files throughout the repository.
