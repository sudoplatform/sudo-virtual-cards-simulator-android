# Agent Operation Instructions

## General Working Principles

### Batch Operations
- When executing multiple related tasks, combine them to minimize user interactions
- Prepare complete migration plans before executing changes

### Error Handling
- Always use `output.txt` files when terminal commands don't show output
- Re-read output files multiple times if they appear empty initially
- Use `--stacktrace` flag for detailed Gradle error information

### Terminal Operations
- **Current directory is always the working directory** - no need to include `cd <directory>` prefix
- Execute commands directly: `./gradlew build` (not `cd /path && ./gradlew build`)
- All relative paths are resolved from the project root directory

### Build Process
1. Always run `formatKotlin` before builds to resolve linting issues
2. Use `./gradlew` commands for all build operations
3. Check `dependencyCheckAnalyze` after dependency updates
4. Update `.mcp-project.json` when making version changes

### Gradle Sync Command
- To sync project with Gradle files: `./gradlew clean build --refresh-dependencies`
- For dependency-only refresh: `./gradlew --refresh-dependencies`

### Adding Files
- Any files related to agent operations should be added to the INTERNAL_ONLY_FILES array in the publish_tag.sh file

## Project-Specific Guidelines

### Architecture
- Android library
- Uses SudoPlatform patterns and conventions
- Focus on security and backward compatibility

### Code Standards
- Follow Kotlin naming conventions (camelCase properties)
- Use `@SerializedName` annotations for JSON compatibility
- Maintain comprehensive vulnerability suppressions

### Testing Strategy
- Run unit tests: `./gradlew test`
- Run device tests: `./gradlew connectedAndroidTest`
- Verify both pass after major changes

### Security
- Keep `dependency-suppression.xml` updated with CVE suppressions
- Document security decisions with expiration dates
- Regular security scans with OWASP dependency check

## Common Task Patterns

### Dependency Updates
1. Update version in `build.gradle` files
2. Run `./gradlew clean build --refresh-dependencies`
3. Run `./gradlew dependencyCheckAnalyze`
4. Add suppressions for new vulnerabilities if needed
5. Update MCP project file with new versions
6. Run `./gradlew checkLicenses` to verify license compliance
7. Add new entries in licenses.yml if needed

### Migration Tasks
1. Update build configuration
2. Fix compilation errors
3. Run `formatKotlin` for code style
4. Update documentation
5. Run full test suite
6. Update MCP configuration files

## File Priority for Updates
1. `build.gradle` (root and module)
2. `dependency-suppression.xml`
3. Documentation files (`README.external.md`, `MCP_README.md`)
4. MCP configuration (`.mcp-project.json`, `mcp.json`)
