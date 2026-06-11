---
name: create-plugin-controller
description: Create or modify a standalone REST controller in a PF4J Spring plugin using only plugin-api and other published contracts. Enforce the shared Constants.API_PREFIX route prefix beginning with /plugin, add DTOs, service delegation, validation, tests, extension registration, and documentation. Do not use for behavior that belongs in HandlerAdvice.
---

# Create Plugin Controller

Implement a plugin-owned HTTP API without depending on host implementation details.

## Contract Boundary

The plugin may use:

- Public classes, interfaces, annotations, DTOs, and errors from `plugin-api`.
- PF4J and Spring APIs declared by the plugin.
- Plugin-owned classes and explicitly declared libraries.

Never import, inspect, or describe a host registry, filter, controller, service
implementation, or path-rewriting mechanism. If endpoint behavior requires a contract
that `plugin-api` does not publish, report the missing contract instead of coupling the
plugin to the host implementation.

## Choose the Extension Type

Create a controller for a standalone HTTP use case owned by the plugin.

Use `$create-handler-advice` when the requirement is to validate, authorize, enrich, or
post-process an operation already represented by the `HandlerAdvice` contract.

## Discover Project Conventions

Before editing:

1. Read `AGENTS.md` and the architecture document when present.
2. Inspect `pom.xml` and the configured `plugin-api` contract.
3. Discover the plugin root package, existing controllers, DTOs, services, tests, and
   `Constants.java`.
4. Search current plugin routes and published API documentation for collisions.
5. Reuse the repository's response envelope and validation conventions.

Useful searches:

```bash
rg -n "class Constants|API_PREFIX|/plugin" src/main/java src/test/java
rg -n "@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)" \
  src/main/java src/test/java
rg -n "implements ExtensionPoint|@Extension" src/main/java
```

Do not search host implementation source to infer endpoint behavior.

## Enforce the API Prefix

Every plugin API must begin with `/plugin`, and the common prefix must be declared once
in the plugin's `Constants.java`.

- Reuse the existing `Constants.API_PREFIX` when it begins with `/plugin`.
- The conventional default for a new plugin is:

```java
public static final String API_PREFIX = "/plugin/api";
```

- If no constants class exists, create one in the plugin's established shared or
  constants package.
- Do not repeat the literal `"/plugin"` in controller annotations.
- Compose the class-level route from `API_PREFIX` and a versioned resource path.
- Reject or fix any new route whose resolved path does not start with `/plugin`.

Minimal constants class:

```java
public final class Constants {

    public static final String API_PREFIX = "/plugin/api";

    private Constants() {
    }
}
```

## Define the HTTP Contract

Determine:

- HTTP method and resolved route.
- API version.
- Path, query, header, and body inputs.
- Jakarta Validation rules.
- Response envelope and error behavior.
- Authorization and data-access contracts exposed by `plugin-api`.
- Idempotency and transaction expectations.

Use only published annotations whose semantics are documented by the shared contract.
Do not assume behavior based on an annotation name.

## Design Components

Follow the repository's existing package layout. If no convention exists:

```text
application/http/controller/
application/http/request/
application/http/response/
domain/service/
domain/service/impl/
shared/Constants.java
```

Keep responsibilities clear:

```text
controller -> plugin use-case service -> plugin-api contract
```

- Controller maps and validates HTTP input, delegates, and maps output.
- Service owns business orchestration.
- DTOs own external serialization and validation shape.
- Infrastructure access goes through public contracts.
- Do not inject host implementation classes.
- Do not perform persistence orchestration directly in the controller.
- Reuse `plugin-api` DTOs and response types instead of creating equivalents.

## Implement the Controller

Use the actual package names and shared response contract discovered in the repository:

```java
import static <plugin-root-package>.shared.Constants.API_PREFIX;

@RestController
@RequestMapping(API_PREFIX + "/v1/examples")
@RequiredArgsConstructor
@Extension
public class ExampleController implements ExtensionPoint {

    private final ExampleService exampleService;

    @PostMapping
    public CommonResponse create(
            @RequestBody @Valid @NotNull ExampleRequest request) {
        return CommonResponse.builder()
                .maLoi(MessageCode.THANH_CONG)
                .moTa(MessageCode.THANH_CONG.getValue())
                .duLieu(exampleService.execute(request))
                .build();
    }
}
```

Adapt the response to the configured `plugin-api` version. Preserve:

- `@RestController`.
- `@Extension`.
- `implements ExtensionPoint`.
- Constructor injection.
- A class-level mapping composed from `Constants.API_PREFIX`.

Do not add manual endpoint-registration code.

## Define DTOs and Services

- Use Jakarta Validation for required and nested input.
- Use the Jackson package and naming strategy already selected by the project.
- Validate or allowlist dynamic namespace, collection, field, sort, and filter values.
- Do not expose database-native objects unless the public API contract requires them.
- Use shared errors such as `AppException` and `MessageCode` when appropriate.
- Add a service interface only when it matches the local pattern or creates a useful
  boundary.
- Use only interfaces published by `plugin-api` for capabilities supplied at runtime.

For asynchronous work, use an executor exposed through a public contract or a
plugin-owned bounded executor. Explicitly propagate and clear required context, and
define partial-versus-total failure behavior.

## Test

Add focused controller and service tests:

- Assert `Constants.API_PREFIX` starts with `/plugin`.
- Verify the resolved class-level route uses `API_PREFIX`.
- Verify HTTP method, request validation, content type, and response envelope.
- Verify service delegation and error propagation.
- Verify authorization-sensitive and asynchronous paths when applicable.

Mock `plugin-api` interfaces and plugin-owned collaborators only. Do not require host
implementation classes for unit tests.

## Document and Validate

Update the plugin API documentation with the resolved route, request, response, errors,
authorization assumptions, and new configuration.

Run:

```bash
mvn test
mvn clean package
git diff --check
```

Locate and inspect the artifact without assuming a plugin name:

```bash
JAR="$(find target -maxdepth 1 -type f -name '*.jar' \
  ! -name 'original-*' ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
  | sort | tail -n 1)"
test -n "$JAR"
unzip -p "$JAR" META-INF/extensions.idx
```

Confirm that the controller is indexed exactly once, every new resolved API path starts
with `/plugin`, no local route collision exists, and shared dependencies are not
packaged accidentally. Report the route, files added, tests, build result, and any
missing shared contract.
