# Compatibility notes

## Findings

- Paper documents `plugin.yml` as the main descriptor for commands, permissions, and API version.
- Paper documents that `api-version` controls the minimum server API version that will load the plugin; a server lower than the declared value refuses to load it.
- Paper's API documentation warns that Bukkit API stability is not guaranteed across major versions, so a broad single-JAR target should use long-standing Bukkit APIs and avoid modern-only Paper/Adventure APIs.
- The current plugin uses Java 16+ pattern matching, Java 14+ switch-arrow syntax, and Adventure `Component` messages. These should be replaced with Java 8-compatible syntax and Bukkit `String` messages for an older target.
- A practical single-JAR compatibility target is Paper 1.16.5 through the current 1.21.x line. It preserves the existing spectator, command, configuration, and entity APIs while avoiding a fragile target as old as 1.8.

## Official references

1. https://docs.papermc.io/paper/dev/plugin-yml/
2. https://docs.papermc.io/paper/dev/project-setup/
3. https://jd.papermc.io/paper
