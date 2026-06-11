---
name: create-handler-advice
description: Create or modify a HandlerAdvice extension in a PF4J Spring plugin using only contracts published by plugin-api. Use for validation, authorization, request enrichment, or post-processing around a host operation. Do not use for standalone HTTP endpoints or when the requested behavior requires an unpublished host implementation detail.
---

# Create HandlerAdvice

Implement a `HandlerAdvice` as a plugin-owned adapter to the public `plugin-api`
contract.

## Contract Boundary

The plugin may know:

- Types and behavior explicitly published by `plugin-api`.
- PF4J and Spring extension contracts declared as plugin dependencies.
- Its own source, configuration, tests, and documentation.
- Other libraries explicitly declared for plugin use.

The plugin must not:

- Import, inspect, cast to, or depend on host implementation classes.
- Infer callback behavior from a host registry, processor, filter, or service
  implementation.
- Copy a host class into the plugin.
- Depend on another plugin's implementation.

If `plugin-api` does not expose enough information to implement the requirement safely,
report the missing contract. Add or change the shared contract before implementing the
plugin behavior.

## Discover Project Conventions

Before editing:

1. Read the repository `AGENTS.md` and architecture document when present.
2. Inspect `pom.xml` to identify the exact `plugin-api`, PF4J, Spring, Java, Jackson,
   and MongoDB versions.
3. Locate the plugin root package, bootstrap configuration, existing advice, services,
   and tests. Do not assume a package or plugin ID.
4. Read `HandlerAdvice`, `HandlerAdviceKey`, `OpenAPI.Type`, and related public types
   from the configured `plugin-api` source or JAR.
5. Read the nearest existing advice only as a local convention, not as proof of
   undocumented runtime behavior.

Useful searches:

```bash
rg -n "implements HandlerAdvice|new HandlerAdviceKey" src/main/java src/test/java
rg -n "plugin-api|pf4j|jackson|mongodb" pom.xml
```

## Define the Advice Contract

Determine from the requirement, plugin configuration, and public contract:

- Namespace.
- Collection or resource identifier.
- `OpenAPI.Type`.
- Callback to implement.
- Ordinal when the contract and local advice require ordering.
- Whether the advice validates, authorizes, enriches, or post-processes.
- Whether it uses the `MongoDatabase` and `ClientSession` supplied to the callback.

Treat any key field change as a routing-contract change. Search the current plugin for
duplicate keys and ordinals. Do not claim ordering or key-normalization semantics that
are not documented by `plugin-api`.

## Design Responsibilities

Keep the advice thin:

```text
HandlerAdvice -> plugin policy/use-case service -> plugin-api contract
```

- Put reusable business policy in a focused plugin service.
- Keep authentication, persistence orchestration, and transaction ownership outside
  the advice unless `plugin-api` explicitly delegates them.
- Obtain identity and infrastructure through published contracts only.
- Use shared exception and error types from `plugin-api` when they fit the contract.
- Do not inject a host implementation class, even if it is available at runtime.

Place the class beside existing advice. If the plugin has no convention, use:

```text
src/main/java/<plugin-root-package>/domain/handler_advice/<feature>/
```

## Implement the Extension

Follow the exact method signatures of the configured `plugin-api` version. A typical
shape is:

```java
@Component
@Extension
public class ExampleHandlerAdvice
        implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;
    private final ExamplePolicyService policyService;

    public ExampleHandlerAdvice(
            Environment environment,
            ExamplePolicyService policyService) {
        this.policyService = policyService;
        this.key = new HandlerAdviceKey(
                environment.getRequiredProperty("<namespace-property>"),
                "<collection>",
                OpenAPI.Type.CREATE,
                0);
    }

    @Override
    public HandlerAdviceKey getKey() {
        return key;
    }

    @Override
    public void beforeProcess(
            MongoDatabase database,
            ClientSession session,
            ObjectNode request) {
        policyService.validate(database, session, request);
    }
}
```

Adapt this example to the actual contract. Do not introduce placeholder fallbacks for
required production configuration unless the plugin already defines that policy.

## Handle Data and MongoDB

- Use read-only JSON access for validation and mutable access only for intentional
  request enrichment.
- Validate request shape before mutation.
- Do not trust client-supplied identity or ownership fields.
- Reuse the supplied `MongoDatabase` and `ClientSession`.
- Never create or close host-owned MongoDB resources.
- Pass the session to MongoDB operations when it is non-null.
- Use BSON builders and minimal projections; avoid query-string concatenation and N+1
  reads.

## Test

Add focused tests in the matching test package:

- `getKey()` fields and ordinal.
- The implemented callback.
- Missing, empty, valid, and invalid request data.
- Authorized and unauthorized behavior when applicable.
- Request mutation when applicable.
- Null and non-null `ClientSession` paths when MongoDB is used.
- Shared exception/error contract.

Mock only public contracts and plugin-owned collaborators. Do not load or mock host
implementation classes.

## Validate

Run the repository's standard checks, normally:

```bash
mvn test
mvn clean package
git diff --check
```

Locate the plugin JAR without assuming its artifact name:

```bash
JAR="$(find target -maxdepth 1 -type f -name '*.jar' \
  ! -name 'original-*' ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
  | sort | tail -n 1)"
test -n "$JAR"
unzip -p "$JAR" META-INF/extensions.idx
```

Confirm that the extension is indexed exactly once, local keys do not collide, and
shared dependencies are not accidentally packaged. Report the key, callback, tests,
build result, and any contract ambiguity.
