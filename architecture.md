# FlexData Plugin Architecture

## 1. Mục đích

Tài liệu này mô tả kiến trúc chuẩn cho các plugin chạy cùng FlexData Core qua PF4J và
`pf4j-spring`.

Mục tiêu:

- Giải thích vì sao plugin được tổ chức theo mô hình hiện tại.
- Xác định ranh giới trách nhiệm giữa core và plugin.
- Đưa ra các invariant không được phá vỡ khi phát triển plugin.
- Cung cấp một kiến trúc nền có thể dùng lại cho nhiều plugin nghiệp vụ.
- Giúp lập trình viên và coding agent phân biệt quyết định kiến trúc với implementation
  cụ thể của từng plugin.

Tài liệu này không mô tả:

- API nghiệp vụ cụ thể.
- Collection hoặc namespace cụ thể.
- Danh sách `HandlerAdvice` của một plugin.
- Property cụ thể của môi trường triển khai.
- Technical debt hoặc lỗi riêng của một repository.

Các nội dung trên phải được ghi trong `README.md`, `AGENTS.md`, issue tracker hoặc tài
liệu riêng của từng plugin.

## 2. Thuật ngữ và placeholder

| Thuật ngữ | Ý nghĩa |
|---|---|
| Core | Ứng dụng FlexData Core chạy Spring Boot và sở hữu hạ tầng chung |
| Plugin | JAR nghiệp vụ được core load tại runtime |
| Shared contract | Interface, DTO, annotation và exception dùng chung |
| Extension | Object plugin công bố cho core qua PF4J |
| Plugin context | Spring child context do plugin tạo |
| Core context | Spring application context chính |

Tài liệu sử dụng các placeholder:

```text
<plugin-id>
<plugin-name>
<plugin-root-package>
<plugin-main-class>
<artifact-id>
<version>
<namespace>
<collection>
```

Khi áp dụng cho plugin cụ thể, thay placeholder trong source và cấu hình. Không cần sửa
tài liệu kiến trúc chung chỉ để thay tên plugin.

## 3. Bối cảnh hệ thống

Plugin là một module Java độc lập được build thành JAR. Plugin không tự chạy bằng
`java -jar`; core nạp và quản lý nó trong cùng JVM.

```text
Client
  |
  v
FlexData Core
  |-- HTTP server
  |-- Security filters and user context
  |-- Common CRUD services
  |-- MongoDB and transactions
  |-- Executor and infrastructure
  |-- PF4J SpringPluginManager
  |-- Endpoint registry
  |-- HandlerAdvice registry
  |
  +---------------- shared contracts ----------------+
  |                                                  |
  v                                                  v
plugin-api/shared libraries                   PF4J ExtensionPoint
  ^                                                  ^
  |                                                  |
  +------------------ Business Plugin ----------------+
                       |
                       |-- plugin controllers
                       |-- HandlerAdvice implementations
                       |-- business services
                       |-- plugin configuration
```

Plugin cho phép bổ sung hoặc thay đổi nghiệp vụ mà không đưa logic ngành dọc vào core và
không cần build lại core cho mỗi thay đổi plugin.

## 4. Ranh giới trách nhiệm

### 4.1 Core sở hữu

Core chịu trách nhiệm:

- Khởi động Spring Boot application.
- Mở HTTP server.
- Xác thực và tạo user/security context.
- Quản lý MongoDB client, database, session và transaction.
- Cung cấp CRUD engine và service dùng chung.
- Cung cấp implementation cho shared contract.
- Quản lý executor, cache, event bus và infrastructure chung.
- Load, start, stop, disable, unload và delete plugin.
- Đăng ký/gỡ endpoint plugin.
- Đăng ký/gỡ `HandlerAdvice`.
- Đồng bộ lifecycle plugin giữa các instance nếu hệ thống hỗ trợ.
- Quản lý logging configuration và observability.

### 4.2 Plugin sở hữu

Plugin chịu trách nhiệm:

- Quy tắc nghiệp vụ riêng.
- Validation và permission policy riêng.
- Controller cho use case không có trong core.
- `HandlerAdvice` cho luồng CRUD đã có trong core.
- DTO và model chỉ thuộc plugin.
- Cấu hình riêng có namespace.
- Dependency chỉ plugin sử dụng.
- Cleanup resource do plugin tự tạo.
- Unit test và integration test cho hành vi plugin.

### 4.3 Chiều phụ thuộc

Chiều phụ thuộc hợp lệ:

```text
plugin -> plugin-api/shared contracts <- core
plugin -> interface/bean do core cung cấp
core   -> PF4J ExtensionPoint <- plugin implementation
```

Chiều phụ thuộc không hợp lệ:

```text
core -> implementation chỉ tồn tại trong plugin
plugin A -> implementation nội bộ của plugin B
shared contract -> plugin cụ thể
plugin -> implementation nội bộ của core khi đã có shared interface
```

Nếu core và plugin cần dùng chung một type, type đó phải nằm trong module shared phù
hợp, không đặt trong plugin cụ thể.

## 5. Mục tiêu kiến trúc

### 5.1 Tách nghiệp vụ khỏi nền tảng

Core giữ vai trò nền tảng. Plugin giữ logic theo ngành hoặc module chức năng.

Hệ quả:

- Plugin có thể phát hành độc lập.
- Core không bị tăng coupling với từng nghiệp vụ.
- Rollback plugin không yêu cầu rollback toàn bộ core.

### 5.2 Tái sử dụng hạ tầng core

Plugin không tạo lại HTTP server, datasource, security stack, logging context hoặc CRUD
engine.

Hệ quả:

- Transaction và security thống nhất.
- Không nhân đôi connection pool và thread pool.
- Cấu hình vận hành tập trung.

### 5.3 Giao tiếp qua contract ổn định

Core và plugin trao đổi qua interface, DTO và extension point dùng chung.

Hệ quả:

- Có thể thay implementation mà không đổi bên còn lại nếu contract tương thích.
- Version contract trở thành một phần của compatibility matrix.
- Shared contract phải có type identity duy nhất trong JVM.

### 5.4 Quản lý được lifecycle runtime

Endpoint, advice và resource phải xuất hiện hoặc biến mất theo trạng thái plugin.

Hệ quả:

- Không đăng ký endpoint hoặc advice bằng static global state.
- Không giữ reference làm plugin classloader không thể được giải phóng.
- Stop/unload/reload là use case bắt buộc phải kiểm thử.

## 6. Cấu trúc runtime

### 6.1 Parent và child Spring context

Core có application context chính. Mỗi plugin tạo một child context:

```text
Core ApplicationContext
  |
  +-- shared services
  +-- shared contracts implementations
  +-- infrastructure beans
  +-- Environment
  |
  +-- parent of -->
      Plugin ApplicationContext
        |
        +-- plugin controllers
        +-- plugin services
        +-- HandlerAdvice implementations
        +-- plugin-owned beans
```

Bootstrap plugin có dạng:

```java
public class ExamplePlugin extends SpringPlugin {

    private final PluginWrapper pluginWrapper;
    private final ApplicationContext parentContext;

    public ExamplePlugin(PluginWrapper wrapper) {
        super(wrapper);
        this.pluginWrapper = wrapper;
        this.parentContext =
                wrapper.getPluginManager() instanceof SpringPluginManager manager
                        ? manager.getApplicationContext()
                        : null;
    }

    @Override
    protected ApplicationContext createApplicationContext() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();

        context.setParent(parentContext);
        context.setClassLoader(pluginWrapper.getPluginClassLoader());
        context.register(PluginConfig.class);
        context.refresh();

        return context;
    }
}
```

Mỗi bước có mục đích:

- `setParent`: plugin inject được bean do core cung cấp.
- `setClassLoader`: Spring resolve class/resource bằng plugin classloader.
- `register`: chỉ đăng ký cấu hình gốc của plugin.
- `refresh`: tạo các bean trong child context.

Không đưa toàn bộ bean plugin trực tiếp vào core context vì sẽ:

- Làm lifecycle plugin phụ thuộc lifecycle core.
- Tăng nguy cơ trùng bean.
- Làm unload khó hoặc không thể.
- Cho plugin override nhầm configuration nền tảng.

### 6.2 Component scan giới hạn

Plugin chỉ scan root package của chính nó:

```java
@Configuration
@ComponentScan("<plugin-root-package>")
public class PluginConfig {
}
```

Không scan package core hoặc package gốc quá rộng.

Root package đề xuất:

```text
com.fds.flexdata.plugin.<plugin-id>
```

## 7. Classloader và type identity

JVM xác định một class bằng:

```text
fully-qualified class name + defining classloader
```

Hai class cùng package và tên nhưng do hai classloader nạp có thể là hai type khác nhau.

Vì vậy plugin không được đóng gói lại các dependency do core cung cấp:

- PF4J và `pf4j-spring`.
- `plugin-api` và module shared.
- Spring Framework và Spring Boot.
- MongoDB driver.
- Jakarta Validation.
- Logging API và implementation.
- Dependency chỉ dùng lúc compile, ví dụ Lombok.

Các dependency đó phải dùng scope `provided`.

Nếu vi phạm, lỗi có thể là:

- `ClassCastException`.
- `NoSuchMethodError`.
- `AbstractMethodError`.
- Spring không inject được bean.
- PF4J không nhận extension.
- Nhiều logging provider.
- Serialization hoặc MongoDB driver không tương thích.

Shade plugin chỉ được đóng gói dependency mà plugin thực sự sở hữu và core không cung
cấp.

## 8. Lifecycle plugin

### 8.1 Load và start

Luồng khái quát:

```text
Core startup hoặc upload API
        |
        v
PF4J đọc plugin.properties
        |
        v
PF4J tạo plugin main class
        |
        v
Plugin tạo child Spring context
        |
        v
PF4J đọc META-INF/extensions.idx
        |
        v
Core đăng ký extension
        |
        +--> controller endpoints
        |
        +--> HandlerAdvice
```

### 8.2 Stop và disable

Khi plugin stop hoặc disable:

- Core gỡ endpoint.
- Core refresh registry/cache liên quan.
- Advice không còn được gọi.
- Plugin không được nhận request mới.

### 8.3 Unload và delete

Khi unload hoặc delete:

- Endpoint và advice phải được unregister.
- Spring context plugin phải đóng.
- Thread, scheduler, listener và resource plugin phải được giải phóng.
- Không còn strong reference tới class hoặc classloader plugin.

### 8.4 Reload

Reload phải tạo được một plugin instance và classloader mới mà không phụ thuộc state
tĩnh của lần chạy trước.

Không dùng:

- Static mutable collection chứa extension.
- Static reference tới Spring bean.
- Thread tự tạo không có shutdown.
- Listener không unregister.

## 9. Extension model

Một class plugin có thể cần tham gia cả Spring và PF4J:

```java
@Component
@Extension
public class ExampleExtension implements ExtensionPoint {
}
```

Vai trò:

| Thành phần | Vai trò |
|---|---|
| Spring stereotype | Tạo bean và inject dependency trong plugin context |
| `@Extension` | Đưa class vào PF4J extension index |
| `ExtensionPoint` | Đánh dấu implementation có thể được PF4J công bố |

Core không quét toàn bộ bean trong child context để tìm extension. Extension phải xuất
hiện trong:

```text
META-INF/extensions.idx
```

File này do PF4J annotation processor sinh ra. Không sửa thủ công.

## 10. Chọn controller hay HandlerAdvice

Quy tắc quyết định:

```text
Use case đã có endpoint/CRUD flow trong core?
  |
  +-- Có --> Cần chèn validation/permission/mutation?
  |            |
  |            +-- Có --> HandlerAdvice
  |            +-- Không --> Không tạo endpoint trùng
  |
  +-- Không --> Plugin có sở hữu HTTP use case độc lập?
               |
               +-- Có --> Plugin controller
               +-- Không --> Service/extension contract phù hợp khác
```

Không viết controller plugin chỉ để nhân đôi API CRUD chung của core.

## 11. Controller architecture

### 11.1 Vai trò

Controller plugin dùng cho HTTP use case độc lập mà core chưa cung cấp.

Luồng:

```text
HTTP request
  |
  v
Core security/filter chain
  |
  v
Core RequestMappingHandlerMapping
  |
  v
Plugin controller
  |
  v
Plugin use-case service
  |
  v
Shared core contracts / plugin-owned dependency
```

### 11.2 Cấu trúc bắt buộc

```java
@RestController
import static <plugin-root-package>.shared.Constants.API_PREFIX;

@RequestMapping(API_PREFIX + "/v1/examples")
@RequiredArgsConstructor
@Extension
public class ExampleController implements ExtensionPoint {

    private final ExampleService exampleService;

    @PostMapping
    public CommonResponse execute(
            @RequestBody @Valid @NotNull ExampleRequest request) {
        return CommonResponse.builder()
                .maLoi(MessageCode.THANH_CONG)
                .moTa(MessageCode.THANH_CONG.getValue())
                .duLieu(exampleService.execute(request))
                .build();
    }
}
```

Controller phải:

- Có Spring controller stereotype.
- Có `@Extension`.
- Implement `ExtensionPoint`.
- Dùng constructor injection.
- Chỉ map HTTP input/output và điều phối.
- Không chứa business logic phức tạp.
- Không tự đăng ký mapping.

Core chịu trách nhiệm đăng ký và gỡ mapping theo lifecycle.

### 11.3 Route

Route nên:

- Dùng `Constants.API_PREFIX`; giá trị prefix phải bắt đầu bằng `/plugin`.
- Có version.
- Không trùng core hoặc plugin khác.
- Không dùng regex rộng hơn nhu cầu.
- Chỉ dựa trên security contract được công bố cho plugin.

Annotation proxy đặc biệt của core chỉ được dùng khi đã hiểu contract tương ứng.

## 12. HandlerAdvice architecture

### 12.1 Vai trò

`HandlerAdvice` chèn logic plugin vào CRUD flow đã có của core mà không viết lại
controller, transaction, audit, logging và response handling.

Use case:

- Validation trước khi persist.
- Permission check.
- Request enrichment.
- Hậu xử lý sau thành công.
- Logic khi core throw exception.

### 12.2 Callback

Contract tổng quát:

```java
public interface HandlerAdvice {

    HandlerAdviceKey getKey();

    default void beforeProcess(
            MongoDatabase database,
            ClientSession session,
            ObjectNode request) {
    }

    default void afterProcess(
            MongoDatabase database,
            ClientSession session,
            ObjectNode request,
            Object result) {
    }

    default void afterThrow(
            MongoDatabase database,
            ClientSession session,
            ObjectNode request,
            Exception exception) {
    }
}
```

### 12.3 Routing key

Advice được chọn bởi:

```text
namespace + normalized collection name + OpenAPI.Type
```

`ordinal` chỉ dùng để sắp xếp nhiều advice cùng key.

Hệ quả:

- Sai namespace, collection hoặc operation làm advice không chạy.
- Hai advice cùng key cần ordinal khác nhau.
- Không dựa vào thứ tự component scan.
- Thay key là thay đổi runtime routing.

### 12.4 Cấu trúc implementation

```java
@Component
@Extension
public class ExampleCreateHandlerAdvice
        implements HandlerAdvice, ExtensionPoint {

    private final HandlerAdviceKey key;
    private final ExamplePolicyService policyService;

    public ExampleCreateHandlerAdvice(
            Environment environment,
            ExamplePolicyService policyService) {
        this.policyService = policyService;

        String namespace = environment.getRequiredProperty(
                "app.plugin.<plugin-id>.namespace");

        this.key = new HandlerAdviceKey(
                namespace,
                "<collection>",
                OpenAPI.Type.CREATE,
                100);
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

Advice nên là integration hook mỏng. Logic dùng lại phải nằm trong policy/service.

## 13. Package và layer

Cấu trúc đề xuất:

```text
<plugin-root-package>
├── application
│   └── http
│       ├── controller
│       ├── request
│       └── response
├── configuration
├── domain
│   ├── dto
│   ├── handler_advice
│   └── service
│       └── impl
├── infrastructure
├── service
└── shared
```

Trách nhiệm:

| Package | Trách nhiệm |
|---|---|
| `application.http` | HTTP adapter và DTO biên |
| `configuration` | Bootstrap PF4J và Spring configuration |
| `domain.handler_advice` | Hook vào flow core |
| `domain.service` | Use case và business abstraction |
| `infrastructure` | Adapter plugin sở hữu |
| `service` | Policy/helper nghiệp vụ dùng chung |
| `shared` | Utility không state và không phụ thuộc Spring |

Chiều dependency mong muốn:

```text
controller -> use-case service -> shared core contracts
HandlerAdvice -> policy service -> shared core contracts
infrastructure -> external/plugin-owned dependency
configuration -> plugin components
```

Không để domain phụ thuộc controller hoặc utility giữ Spring bean.

## 14. Core-provided services

Plugin có thể inject các interface do core implement, ví dụ:

- CRUD/common function service.
- Data access service.
- Authorization resolver.
- Lock service.
- Event publisher.
- Executor service.
- Environment.

Nguyên tắc:

- Inject interface shared.
- Không cast sang implementation nội bộ.
- Không tạo bản sao interface trong plugin.
- Không dùng reflection để truy cập API nội bộ khi có contract chính thức.

Nếu cần capability mới:

1. Thiết kế contract shared.
2. Core implement contract.
3. Plugin phụ thuộc contract với `provided`.
4. Plugin inject interface.

## 15. Security và permission

Core sở hữu authentication. Plugin không tự parse token.

```text
Core security filter
  |
  v
Shared user/security context
  |
  v
Plugin policy service
  |
  v
Allow hoặc AppException
```

Plugin chịu trách nhiệm authorization nghiệp vụ riêng.

Tách:

- Authentication: người dùng là ai, thuộc core.
- Authorization: người dùng có được thao tác hay không.
- Data integrity: dữ liệu tham chiếu có hợp lệ hay không.

Không tin các field identity, owner hoặc scope do client gửi nếu chúng phải đến từ user
context đã xác thực.

Policy dùng lại phải nằm trong service riêng, không copy vào nhiều advice/controller.

## 16. Request mutation

`beforeProcess` có thể thay đổi `ObjectNode` trước khi core xử lý.

Đọc body mà không tạo mới:

```java
JsonNode body = request.path("Body");
```

Chủ đích tạo/mutate body:

```java
ObjectNode body = request.withObjectProperty("Body");
```

`withObjectProperty` có thể tạo object khi field chưa tồn tại. Chỉ dùng khi đó là contract
mong muốn.

Quy tắc:

- Chỉ mutate field plugin sở hữu.
- Không xóa field core cần.
- Dữ liệu tin cậy phải lấy từ context/core service.
- Xác định rõ validation chạy trước hay sau mutation.
- Test cả request thiếu field và request cố tình gửi field giả.

## 17. MongoDB và transaction

Core có thể truyền:

```java
MongoDatabase database
ClientSession session
```

Plugin không sở hữu hai resource này.

Quy tắc:

- Không close database hoặc session.
- Không tự tạo `MongoClient`.
- Không mở transaction mới nếu cần tham gia transaction hiện tại.
- Hỗ trợ cả `session == null` và `session != null`.
- Dùng BSON API thay vì ghép query bằng chuỗi.

Pattern:

```java
var iterable = session != null
        ? collection.find(session, filter)
        : collection.find(filter);
```

Validation query nên:

- Filter đúng key.
- Project đúng field cần thiết.
- Tránh N+1 nếu có thể dùng `$in`.
- Không load document đầy đủ khi chỉ cần existence hoặc một vài field.

## 18. Configuration architecture

### 18.1 Nguồn cấu hình

Plugin dùng `Environment` của hệ thống. Property có thể đến từ:

- Core local config.
- Consul.
- Environment variable.
- JVM system property.
- File config trong JAR plugin.

### 18.2 Namespace property

Property riêng phải dùng:

```text
app.plugin.<plugin-id>.*
```

Ví dụ:

```yaml
app:
  plugin:
    example:
      namespace: example-namespace
      feature-enabled: true
```

Không dùng key toàn cục như:

```text
server.*
logging.*
management.*
spring.datasource.*
spring.data.mongodb.*
```

### 18.3 Thứ tự nạp

Core implementation có thể thêm config plugin sau khi plugin start. Nếu vậy, constructor
bean có thể chạy trước khi property trong JAR sẵn sàng.

Do đó:

- Config cần lúc tạo bean phải đến từ core/Consul/environment.
- Config bắt buộc dùng `getRequiredProperty`.
- Default value chỉ dùng khi thực sự an toàn.
- Thay đổi config loading phải được kiểm tra trong source core.

### 18.4 Secret

Không đóng gói:

- Password.
- Token.
- Private key.
- Credential.
- Connection string chứa secret.

## 19. Serialization và error contract

Plugin phải dùng cùng major version serialization library với core.

Nếu hệ thống dùng Jackson 3:

```text
tools.jackson.*
```

không trộn với Jackson 2:

```text
com.fasterxml.jackson.*
```

trừ khi có boundary và dependency isolation rõ ràng.

DTO naming strategy phải theo external contract.

Lỗi dùng shared error contract:

- `AppException`.
- `MessageCode`.
- Structured detail error.
- Shared response envelope khi phù hợp.

Không tự tạo global exception handler nếu core đã xử lý được exception plugin.

## 20. Async và context propagation

User context, MDC và các dữ liệu `ThreadLocal` không tự truyền sang worker thread.

Pattern:

```java
return CompletableFuture.supplyAsync(() -> {
    try {
        restoreContext(capturedContext);
        return executeBusiness();
    } finally {
        clearContext();
    }
}, executorService);
```

Quy tắc:

- Dùng executor có giới hạn, ưu tiên executor của core.
- Capture context tại request thread.
- Restore tại worker.
- Clear trong `finally`.
- Không dùng common pool khi operation cần context hoặc resource limit rõ ràng.
- Xác định response all-or-nothing hay partial success.
- Test exception path.

## 21. Logging và observability

Plugin dùng logging facade của core:

```java
log.info("Processing entity id={}", id);
```

Không:

- Dùng `System.out` hoặc `System.err`.
- Khởi tạo logging context riêng.
- Đóng gói logging implementation nếu core đã cung cấp.
- Log token, password hoặc dữ liệu nhạy cảm.
- Thêm `log4j2-spring.xml` riêng nếu không có cơ chế hợp nhất được thiết kế rõ.

Metric, tracing và correlation context phải theo infrastructure core.

## 22. Build artifact

Plugin build thành:

```text
target/<artifact-id>-<version>.jar
```

Artifact tối thiểu:

```text
plugin.properties
application.yml
META-INF/extensions.idx
<plugin classes>
<plugin-owned dependencies>
```

Descriptor:

```properties
plugin.id=<plugin-id>
plugin.version=<version>
plugin.class=<plugin-main-class>
```

JAR không cần `Main-Class`.

Không được chứa bản sao dependency shared do core cung cấp.

## 23. Testing architecture

### 23.1 Unit test

Test:

- Business service.
- Permission policy.
- Relation validation.
- Request mutation.
- Error mapping.
- Async context cleanup.

Mock shared core contracts.

### 23.2 Contract test

Test:

- `HandlerAdviceKey`.
- Extension annotation/metadata.
- Descriptor fields.
- Serialization naming.
- Compatibility với `plugin-api` version.

### 23.3 Controller test

Test:

- Route và HTTP method.
- Validation.
- Response envelope.
- Error propagation.
- Security-sensitive input constraints.

### 23.4 Integration test với core

Test:

1. Load plugin.
2. Start plugin.
3. Controller được đăng ký.
4. Advice được gọi đúng operation.
5. Stop gỡ endpoint/advice.
6. Start lại hoạt động.
7. Unload/reload không rò resource.

## 24. Architectural decisions

### ADR-001: Dùng runtime plugin thay vì compile-time module

**Quyết định:** nghiệp vụ mở rộng được đóng gói thành PF4J plugin JAR.

**Lý do:** phát hành độc lập và giữ core tập trung vào nền tảng.

**Hệ quả:** phải quản lý compatibility, classloader và lifecycle.

### ADR-002: Mỗi plugin có child Spring context

**Quyết định:** plugin context dùng core context làm parent.

**Lý do:** tái sử dụng bean core nhưng cô lập bean/lifecycle plugin.

**Hệ quả:** core chỉ gọi ngược qua extension hoặc contract chính thức.

### ADR-003: Shared contract nằm ngoài plugin

**Quyết định:** interface và DTO dùng chung nằm trong shared library.

**Lý do:** tránh dependency ngược và giữ type identity.

**Hệ quả:** shared dependency phải `provided` và version đồng bộ.

### ADR-004: Dùng HandlerAdvice cho CRUD extension

**Quyết định:** không viết lại CRUD controller khi chỉ cần chèn nghiệp vụ.

**Lý do:** tái sử dụng transaction, validation, audit và response flow của core.

**Hệ quả:** routing key là runtime contract quan trọng.

### ADR-005: Controller plugin là PF4J extension

**Quyết định:** controller được core đăng ký động từ extension list.

**Lý do:** endpoint phải theo lifecycle plugin.

**Hệ quả:** controller cần Spring stereotype, `@Extension` và `ExtensionPoint`.

### ADR-006: Core sở hữu infrastructure

**Quyết định:** plugin inject infrastructure qua shared interface.

**Lý do:** thống nhất resource, transaction, security và observability.

**Hệ quả:** plugin không tự bootstrap infrastructure trùng core.

### ADR-007: Config plugin có namespace riêng

**Quyết định:** dùng `app.plugin.<plugin-id>.*`.

**Lý do:** tránh override property toàn cục.

**Hệ quả:** deployment config phải cung cấp đúng namespace.

### ADR-008: Resource phải theo lifecycle plugin

**Quyết định:** mọi resource plugin sở hữu phải có cleanup.

**Lý do:** hỗ trợ stop/unload/reload an toàn.

**Hệ quả:** không dùng static state hoặc thread không quản lý.

## 25. Invariants

1. Core không phụ thuộc compile-time vào plugin cụ thể.
2. Plugin giao tiếp với core qua shared contract.
3. Plugin context giữ core context làm parent.
4. Plugin context dùng plugin classloader.
5. Component scan không vượt khỏi root package plugin.
6. Controller/advice cần PF4J extension metadata.
7. Shared dependency không được shade vào plugin JAR.
8. Advice key phải khớp namespace, collection và operation.
9. Advice dùng MongoDB session của core khi có.
10. Plugin không đóng resource do core sở hữu.
11. Async context được clear trong `finally`.
12. Config riêng có namespace và không chứa secret.
13. Endpoint/advice biến mất khi plugin stop hoặc unload.
14. Resource plugin được cleanup khi context đóng.
15. Thay đổi kiến trúc phải cập nhật tài liệu này.

## 26. Cấu trúc tài liệu cho từng plugin

Giữ `architecture.md` chung và đưa chi tiết plugin vào `README.md` hoặc tài liệu riêng:

### Plugin identity

```text
Plugin ID:
Artifact:
Main class:
Root package:
Core version compatibility:
```

### Plugin-owned capabilities

```text
Controllers:
HandlerAdvices:
Business services:
External integrations:
```

### HandlerAdvice registry

| Namespace | Collection | Operation | Ordinal | Responsibility |
|---|---|---|---:|---|
| | | | | |

### Controller registry

| Method | Path | Service | Authorization |
|---|---|---|---|
| | | | |

### Configuration

| Property | Required | Default | Source | Description |
|---|---|---|---|---|
| | | | | |

### Known issues

Ghi technical debt của plugin tại đây hoặc issue tracker, không thêm vào kiến trúc chung.

## 27. Checklist thay đổi

### Thêm controller

- Xác nhận core chưa có use case tương đương.
- Có Spring stereotype, `@Extension`, `ExtensionPoint`.
- Route không trùng và có version.
- Business logic nằm trong service.
- Có trong extension index.
- Test start/stop.

### Thêm HandlerAdvice

- Xác nhận routing key với core.
- Kiểm tra key và ordinal trùng.
- Dùng policy service cho logic dùng lại.
- Dùng current session.
- Test empty, success, permission denied và invalid relation.
- Có trong extension index.

### Thêm dependency

- Xác định owner là core hay plugin.
- Core-owned dependency dùng `provided`.
- Kiểm tra shaded JAR.
- Kiểm tra version compatibility.

### Thêm config

- Dùng `app.plugin.<plugin-id>.*`.
- Xác định thời điểm property cần có.
- Không đóng gói secret.
- Cập nhật deployment documentation.

### Thêm async flow

- Dùng bounded/core executor.
- Capture/restore context.
- Clear trong `finally`.
- Xác định failure semantics.
- Test exception path.

### Thay đổi lifecycle

- Test load/start/stop/unload/reload.
- Kiểm tra endpoint/advice registry.
- Kiểm tra thread và classloader leak.
- Cập nhật ADR và invariant nếu contract thay đổi.

## 28. Hướng dẫn cho coding agent

Khi task liên quan kiến trúc:

1. Đọc `AGENTS.md`.
2. Đọc tài liệu này.
3. Đọc phần plugin cụ thể trong `README.md`.
4. Đọc descriptor và config.
5. Đọc bootstrap class và `PluginConfig`.
6. Đọc implementation gần nhất.
7. Đọc shared contract.
8. Đọc đúng version shared contract trong `plugin-api`.

Không suy luận behavior runtime từ implementation nội bộ của core. Plugin chỉ thiết kế
theo contract công khai; nếu contract không mô tả đủ behavior cần thiết, phải bổ sung
contract hoặc tài liệu contract trước khi triển khai.
