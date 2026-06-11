# FlexData Plugin Development Guide

Tài liệu này mô tả quy ước chung để phát triển, build, kiểm thử và triển khai plugin cho
ứng dụng FlexData Core. Nội dung được viết theo hướng dùng lại cho nhiều plugin; thông
tin nghiệp vụ, API và cấu hình riêng của từng plugin nên được bổ sung ở phần
[Tài liệu riêng của plugin](#tài-liệu-riêng-của-plugin).

Kiến trúc chuẩn và các quyết định thiết kế dùng chung cho FlexData plugin được mô tả tại
[architecture.md](architecture.md). Chi tiết nghiệp vụ của từng plugin được bổ sung
riêng trong README hoặc tài liệu module tương ứng.

## 1. Tổng quan

Plugin là một module Java độc lập, được build thành file JAR và được FlexData Core nạp
ở runtime thông qua PF4J và `pf4j-spring`.

Plugin có thể:

- Cung cấp REST controller riêng.
- Cài đặt các extension point được định nghĩa trong thư viện dùng chung.
- Cài đặt `HandlerAdvice` để bổ sung logic trước, sau hoặc khi lỗi trong luồng xử lý dữ
  liệu của core.
- Sử dụng các interface, DTO, annotation và exception trong `plugin-api`.
- Inject các Spring bean được core công khai cho plugin.
- Đọc `Environment` và các property của core.
- Đóng gói thư viện riêng nếu thư viện đó không do core cung cấp.

Plugin không thể:

- Là dependency ngược của core.
- Yêu cầu core import trực tiếp class chỉ tồn tại trong plugin.
- Thay đổi code core chỉ bằng cách khai báo một Spring bean thông thường.
- Tự chạy độc lập bằng `java -jar` nếu không xây dựng thêm một ứng dụng bootstrap
  riêng.

File JAR plugin là artifact để PF4J nạp, không phải Spring Boot executable JAR.

## 2. Kiến trúc tích hợp

### 2.1 Sơ đồ tổng quát

```text
┌──────────────────────────────────────────────────────────────┐
│ FlexData Core                                                │
│                                                              │
│  Spring ApplicationContext                                   │
│  ├── Core services                                           │
│  ├── plugin-api implementations                              │
│  ├── SpringPluginManager                                     │
│  ├── PluginEndpointRegistry                                  │
│  └── HandlerAdviceHolder                                     │
└───────────────────────┬──────────────────────────────────────┘
                        │ parent context / shared contracts
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ Plugin                                                       │
│                                                              │
│  PF4J PluginClassLoader                                      │
│  └── Child AnnotationConfigApplicationContext                │
│      ├── Plugin controllers                                  │
│      ├── Plugin services                                     │
│      ├── HandlerAdvice implementations                       │
│      └── Plugin-owned dependencies                           │
└──────────────────────────────────────────────────────────────┘
```

Mỗi plugin tạo một `AnnotationConfigApplicationContext` riêng:

- Classloader là classloader của plugin.
- Parent context là Spring context của core.
- Bean trong plugin có thể tìm bean từ parent context.
- Bean trong core không tự động nhìn thấy bean thông thường trong child context.
- Core chỉ tương tác ngược với plugin qua PF4J extension hoặc cơ chế đăng ký được thiết
  kế rõ ràng.

### 2.2 Chiều phụ thuộc

Chiều phụ thuộc hợp lệ:

```text
plugin -> plugin-api/shared contracts <- core
plugin -> bean/interface do core cung cấp
core   -> PF4J ExtensionPoint <- plugin implementation
```

Chiều phụ thuộc không hợp lệ:

```text
core -> class nghiệp vụ chỉ tồn tại trong plugin
plugin A -> implementation nội bộ của plugin B
shared API -> plugin cụ thể
```

Nguyên tắc quan trọng:

> Core và plugin phải giao tiếp qua contract ổn định, không giao tiếp bằng implementation
> nội bộ của nhau.

Nếu một kiểu dữ liệu hoặc interface cần được cả hai phía sử dụng, kiểu đó phải nằm trong
`plugin-api` hoặc một thư viện shared được core và plugin cùng phụ thuộc.

### 2.3 Classloader và nhận diện kiểu

Hai class có cùng package và cùng tên nhưng được nạp bởi hai classloader khác nhau vẫn
có thể bị JVM xem là hai kiểu khác nhau. Vì vậy plugin không được đóng gói lại các thư
viện contract mà core đã cung cấp.

Các dependency sau thường phải dùng scope `provided`:

- `pf4j-spring`
- `plugin-api`
- Các module shared của FlexData
- Spring Framework và Spring Boot starter do core cung cấp
- MongoDB driver do core cung cấp
- Validation API
- Logging API và logging implementation của core

Nếu plugin đóng gói lại các dependency trên, lỗi thường gặp là:

- `ClassCastException` dù tên class giống nhau.
- `NoSuchMethodError` hoặc `AbstractMethodError`.
- Spring không inject được bean theo interface.
- PF4J không nhận diện extension.
- Xuất hiện nhiều SLF4J provider hoặc logging binding.
- Jackson, MongoDB hoặc Spring dùng hai phiên bản không tương thích.

## 3. Phiên bản tương thích

Repository mẫu hiện tại sử dụng:

| Thành phần | Phiên bản |
|---|---:|
| Java | 21 |
| Spring Boot | 4.0.6 |
| `pf4j-spring` | 0.10.0 |
| FlexData `plugin-api` | 2.0.0 |
| Maven Shade Plugin | 3.6.0 |

Đây là một bộ phiên bản tương thích, không phải cam kết rằng mọi phiên bản mới hơn đều
có thể thay thế trực tiếp.

Khi nâng phiên bản:

1. Nâng `plugin-api` đồng bộ với core.
2. Kiểm tra chữ ký các interface mà plugin implement.
3. Kiểm tra phiên bản Java mà core đang chạy.
4. Kiểm tra dependency tree để tránh đóng gói trùng thư viện.
5. Build lại toàn bộ plugin.
6. Chạy kiểm thử tích hợp trên đúng phiên bản core mục tiêu.

Không nên deploy plugin được compile bằng một phiên bản `plugin-api` mới hơn core nếu
chưa xác nhận tương thích nhị phân.

## 4. Cấu trúc project đề xuất

```text
<plugin-project>/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/fds/flexdata/plugin/<plugin>/
    │   │       ├── application/
    │   │       │   └── http/
    │   │       │       ├── controller/
    │   │       │       ├── request/
    │   │       │       └── response/
    │   │       ├── configuration/
    │   │       │   ├── <PluginName>Plugin.java
    │   │       │   └── PluginConfig.java
    │   │       ├── domain/
    │   │       │   ├── dto/
    │   │       │   ├── handler_advice/
    │   │       │   └── service/
    │   │       ├── infrastructure/
    │   │       └── shared/
    │   └── resources/
    │       ├── application.yml
    │       └── plugin.properties
    └── test/
        └── java/
```

Quy ước package:

- Mỗi plugin phải có root package riêng.
- Không đặt class plugin trong package của core.
- Không dùng cùng root package cho nhiều plugin.
- `@ComponentScan` chỉ nên scan root package của chính plugin.

Ví dụ:

```text
com.fds.flexdata.plugin.<plugin_id>
```

## 5. Plugin descriptor

PF4J đọc metadata từ `src/main/resources/plugin.properties`.

Mẫu tối thiểu:

```properties
plugin.id=<plugin-id>
plugin.version=<plugin-version>
plugin.class=com.fds.flexdata.plugin.<plugin>.configuration.<PluginName>Plugin
```

Có thể bổ sung metadata PF4J khác khi cần:

```properties
plugin.provider=<provider>
plugin.description=<description>
```

Ý nghĩa:

| Property | Bắt buộc | Mô tả |
|---|---|---|
| `plugin.id` | Có | ID duy nhất của plugin trong một hệ thống core |
| `plugin.version` | Có | Phiên bản runtime mà PF4J và core ghi nhận |
| `plugin.class` | Có | Fully qualified name của class bootstrap plugin |
| `plugin.provider` | Không | Đơn vị phát triển hoặc cung cấp |
| `plugin.description` | Không | Mô tả ngắn |

Quy tắc:

- `plugin.id` phải ổn định giữa các lần nâng cấp.
- Không tái sử dụng một `plugin.id` cho hai plugin khác chức năng.
- `plugin.class` phải tồn tại trong JAR và kế thừa `SpringPlugin`.
- `plugin.version` nên khớp với `<version>` trong `pom.xml`.
- File phải nằm ở root classpath của JAR, không đặt trong package Java.
- Lưu file bằng UTF-8.

Sau khi build, kiểm tra:

```bash
unzip -p target/<artifactId>-<version>.jar plugin.properties
```

## 6. Bootstrap Spring plugin

### 6.1 Class plugin

Mẫu bootstrap:

```java
package com.fds.flexdata.plugin.example.configuration;

import org.pf4j.PluginWrapper;
import org.pf4j.spring.SpringPlugin;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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

Vai trò của từng bước:

1. Lấy Spring context của core từ `SpringPluginManager`.
2. Tạo context riêng cho plugin.
3. Gắn core context làm parent.
4. Gắn PF4J plugin classloader cho context.
5. Đăng ký cấu hình gốc của plugin.
6. Refresh context để tạo các bean plugin.

Nếu bỏ `context.setParent(parentContext)`, plugin không inject được bean do core cung
cấp.

Nếu bỏ `context.setClassLoader(...)`, component scanning, resource loading hoặc type
resolution có thể dùng sai classloader.

### 6.2 Cấu hình component scan

```java
package com.fds.flexdata.plugin.example.configuration;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.fds.flexdata.plugin.example")
public class PluginConfig {
}
```

Không scan package quá rộng như `com.fds.flexdata`, vì điều đó có thể:

- Tạo lại bean của core trong child context.
- Gây trùng bean name.
- Kéo các configuration không thuộc plugin vào context.
- Tăng thời gian khởi động.

## 7. Extension model

### 7.1 Hai cơ chế đăng ký

Một class có thể cần hai annotation với hai mục đích khác nhau:

```java
@Component
@Extension
public class ExampleExtension implements ExtensionPoint {
}
```

- `@Component`, `@Service`, `@RestController`: đưa class vào Spring context của plugin.
- `@Extension`: đưa class vào danh sách extension của PF4J.
- `implements ExtensionPoint`: đánh dấu class là PF4J extension point implementation.

Core không quét toàn bộ bean trong plugin để tìm controller. Core lấy danh sách từ
`pluginManager.getExtensions(pluginId)`, sau đó mới lọc class có `@Controller` hoặc
`@RestController`.

Vì vậy controller thiếu `@Extension` hoặc không implement `ExtensionPoint` có thể tồn
tại trong plugin context nhưng endpoint vẫn không được đăng ký vào core.

### 7.2 Extension index

PF4J tạo file:

```text
META-INF/extensions.idx
```

Kiểm tra sau khi build:

```bash
unzip -p target/<artifactId>-<version>.jar META-INF/extensions.idx
```

Mọi controller và `HandlerAdvice` cần được core phát hiện phải xuất hiện trong file này.

Nếu extension không xuất hiện:

- Kiểm tra `@Extension`.
- Kiểm tra class có implement `ExtensionPoint`.
- Kiểm tra annotation processing của Maven compiler.
- Chạy `mvn clean package` để tránh dùng index cũ.

## 8. Viết REST controller trong plugin

Mẫu controller:

```java
package com.fds.flexdata.plugin.example.application.http.controller;

import lombok.RequiredArgsConstructor;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plugins/example/v1")
@RequiredArgsConstructor
@Extension
public class ExampleController implements ExtensionPoint {

    private final ExampleService exampleService;

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(exampleService.getStatus());
    }
}
```

Khi plugin được start, core:

1. Lấy extension của plugin.
2. Chọn các extension có `@Controller` hoặc `@RestController`.
3. Đọc Spring request mapping trên class và method.
4. Đăng ký mapping vào `RequestMappingHandlerMapping` của core.
5. Lưu metadata endpoint vào hệ thống quản lý dịch vụ dữ liệu.

Khi plugin bị stop, unload, disable hoặc delete, core gỡ các mapping đã đăng ký.

### 8.1 Quy tắc endpoint

- Dùng prefix riêng cho plugin để giảm nguy cơ trùng endpoint.
- Version API trong path, ví dụ `/v1`.
- Không khai báo cùng method và path với core hoặc plugin khác.
- Không dùng regex path quá rộng nếu không thực sự cần.
- Validate request bằng Jakarta Validation.
- Trả response theo contract chung nếu hệ thống yêu cầu.
- Không giả định endpoint plugin tự động bỏ qua security filter của core.

Core kiểm tra xung đột endpoint giữa các plugin đang hoạt động. Mapping trùng với mapping
đã đăng ký cũng có thể làm Spring từ chối đăng ký plugin.

### 8.2 Annotation xử lý của core

Core có thể tạo proxy cho controller method sử dụng các annotation contract như:

- `@MessageProcessor`
- `@DataAccess`
- `@ResourceLocks`

Chỉ sử dụng các annotation này khi đã hiểu contract của core và đã kiểm thử tích hợp.
Không tự định nghĩa annotation cùng tên trong plugin.

## 9. HandlerAdvice

### 9.1 Mục đích

`HandlerAdvice` cho phép plugin chèn logic vào luồng xử lý dữ liệu chung của core mà
không sửa controller hoặc service trong core.

Contract hiện tại:

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

Có thể override một hoặc nhiều callback:

| Callback | Thời điểm | Mục đích phổ biến |
|---|---|---|
| `beforeProcess` | Trước logic chính | Validate, phân quyền, chuẩn hóa request |
| `afterProcess` | Sau khi xử lý thành công | Bổ sung logic hậu xử lý |
| `afterThrow` | Khi luồng chính phát sinh exception | Theo dõi hoặc xử lý bổ sung |

Không nuốt exception trong `afterThrow` nếu contract nghiệp vụ yêu cầu lỗi được trả về
client.

### 9.2 Mẫu implementation

```java
package com.fds.flexdata.plugin.example.domain.handler_advice;

import com.fds.flexdata.pluginapi.HandlerAdvice;
import com.fds.flexdata.pluginapi.annotation.OpenAPI;
import com.fds.flexdata.pluginapi.query.HandlerAdviceKey;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

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
                "app.plugin.example.namespace");

        this.key = new HandlerAdviceKey(
                namespace,
                "T_Example",
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

        policyService.validate(request);
    }
}
```

### 9.3 HandlerAdviceKey

`HandlerAdviceKey` định danh luồng mà advice sẽ tham gia:

```text
namespace + collectionName + OpenAPI.Type
```

Constructor:

```java
new HandlerAdviceKey(namespace, collectionName, type);
new HandlerAdviceKey(namespace, collectionName, type, ordinal);
```

`ordinal` điều khiển thứ tự advice cho cùng một key. Core sắp xếp bằng `TreeSet` chỉ dựa
trên `ordinal`.

Do đó:

- Mỗi advice có cùng key phải dùng `ordinal` khác nhau.
- Không nên phụ thuộc vào thứ tự scan class.
- Nên dùng khoảng số để dễ chèn logic, ví dụ `100`, `200`, `300`.
- Nếu nhiều advice cùng key có cùng `ordinal`, một advice có thể bị loại khỏi
  `TreeSet`.

### 9.4 Chọn OpenAPI.Type

Một số type thường dùng:

- `CREATE`
- `UPDATE`
- `UPSERT`
- `READ`
- `READ_DETAIL`
- `DELETE`
- `RESTORE`
- `SEARCH`
- `IMPORT`
- `EXPORT`
- Các type liên quan draft, history và batch

Type phải khớp chính xác với operation mà core tạo `HandlerAdviceKey`. Sai namespace,
collection hoặc type sẽ khiến advice không bao giờ được gọi dù extension đã được load.

### 9.5 Transaction và MongoDB session

`ClientSession` có thể là `null`, tùy luồng gọi. Nếu plugin truy vấn MongoDB trực tiếp:

```java
if (session != null) {
    collection.find(session, filter);
} else {
    collection.find(filter);
}
```

Không tự mở transaction mới nếu cần tham gia transaction của core. Không đóng
`MongoDatabase`, `MongoClient` hoặc `ClientSession` do core truyền vào.

## 10. Sử dụng bean và API do core cung cấp

Vì plugin context có parent là core context, plugin có thể constructor-inject bean theo
interface:

```java
@Service
@RequiredArgsConstructor
public class ExampleService {

    private final CommonFunctionHandler commonFunctionHandler;
    private final FlexDigitalCommonService flexDigitalCommonService;
    private final ExecutorService executorService;
}
```

Các interface phải nằm trong dependency shared, ví dụ `plugin-api`.

Quy tắc:

- Ưu tiên inject interface, không inject implementation nội bộ của core.
- Không dùng field injection.
- Không tự tạo bean có cùng tên hoặc cùng contract để thay thế bean core ngoài chủ đích.
- Không giữ reference static tới Spring bean hoặc ApplicationContext.
- Không cast bean core sang implementation mà plugin tự khai báo.

Nếu cần thêm khả năng mới từ core:

1. Thiết kế interface trong module contract dùng chung.
2. Core cung cấp implementation và Spring bean.
3. Plugin phụ thuộc contract với scope `provided`.
4. Plugin inject interface.

Không giải quyết bằng cách thêm dependency từ core sang plugin.

## 11. Environment và cấu hình

### 11.1 Đọc cấu hình của core

Plugin có thể inject `Environment`:

```java
@Component
public class ExampleProperties {

    private final String namespace;

    public ExampleProperties(Environment environment) {
        this.namespace = environment.getRequiredProperty(
                "app.plugin.example.namespace");
    }
}
```

Hoặc dùng `@Value`:

```java
@Value("${app.plugin.example.namespace:csdl-example}")
private String namespace;
```

Vì context plugin có parent context, `Environment` có thể đọc property từ:

- Cấu hình local của core.
- Environment variable.
- System property.
- Consul hoặc nguồn cấu hình ngoài mà core đã nạp.
- Property source được core bổ sung cho plugin.

### 11.2 File application.yml của plugin

Core mặc định tìm:

```text
application.yml
```

Tên file có thể được cấu hình tại core:

```yaml
app:
  plugin:
    app-property-file-name: application.yml
```

File phải nằm tại:

```text
src/main/resources/application.yml
```

và sau build phải có ở root JAR:

```bash
unzip -p target/<artifactId>-<version>.jar application.yml
```

Mẫu cấu hình an toàn:

```yaml
app:
  plugin:
    example:
      namespace: csdl-example
      feature-enabled: true
      batch-size: 100
```

Mỗi plugin phải namespace property bằng `plugin.id`:

```text
app.plugin.<plugin-id>.*
```

Không nên đặt các key toàn cục sau trong file plugin:

- `spring.application.name`
- `server.*`
- `spring.datasource.*`
- `spring.data.mongodb.*`
- `logging.*`
- `management.*`
- Các key `app.*` chung không có namespace plugin

Core thêm property của plugin vào đầu danh sách property source với tên:

```text
plugin::<pluginId>
```

Điều đó có nghĩa property plugin có thể override property độ ưu tiên thấp hơn của toàn
bộ core, không chỉ child context của plugin. Một key quá chung có thể làm thay đổi hành
vi toàn ứng dụng.

### 11.3 Lưu ý về thời điểm nạp cấu hình

Theo implementation core hiện tại, `application.yml` của plugin được thêm vào
`Environment` sau khi PF4J đã gọi start plugin. Trong khi đó Spring context và bean của
plugin được tạo trong quá trình start.

Hệ quả:

- Property đã có sẵn từ core/Consul có thể đọc trong constructor plugin.
- Property chỉ tồn tại trong `application.yml` bên trong JAR có thể chưa sẵn sàng khi
  constructor plugin chạy.
- Giá trị mặc định trong `getProperty(key, defaultValue)` có thể được dùng dù file plugin
  có khai báo key đó.

Với cấu hình bắt buộc ngay lúc tạo bean, nên khai báo trong cấu hình core/Consul. Với
cấu hình chỉ nằm trong JAR, nên đọc tại thời điểm sử dụng hoặc điều chỉnh core để nạp
property trước khi start plugin.

Không dùng `getProperty(..., defaultValue)` cho cấu hình bảo mật hoặc cấu hình bắt buộc
nếu giá trị mặc định có thể che giấu lỗi triển khai. Dùng `getRequiredProperty` và fail
fast khi phù hợp.

### 11.4 Cấu hình bí mật

Không đưa vào JAR:

- Password.
- Access token.
- Private key.
- Connection string chứa credential.
- Secret dùng để ký hoặc mã hóa.

Secret phải do môi trường triển khai của core cung cấp.

## 12. Maven và dependency

### 12.1 POM tham khảo

```xml
<properties>
    <java.version>21</java.version>
    <flexdata.version>2.0.0</flexdata.version>
    <pf4j-spring.version>0.10.0</pf4j-spring.version>
    <maven-shade-plugin.version>3.6.0</maven-shade-plugin.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.pf4j</groupId>
        <artifactId>pf4j-spring</artifactId>
        <version>${pf4j-spring.version}</version>
        <scope>provided</scope>
    </dependency>

    <dependency>
        <groupId>flexdata.core</groupId>
        <artifactId>plugin-api</artifactId>
        <version>${flexdata.version}</version>
        <scope>provided</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <scope>provided</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
        <scope>provided</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- Thư viện riêng của plugin, sẽ được đóng gói vào shaded JAR. -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
        <optional>true</optional>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 12.2 Chọn scope

| Loại dependency | Scope đề xuất | Đóng gói vào plugin |
|---|---|---|
| Contract core/plugin | `provided` | Không |
| PF4J, Spring, MongoDB, logging do core cung cấp | `provided` | Không |
| Thư viện chỉ dùng khi compile | `provided` | Không |
| Thư viện riêng của plugin | mặc định | Có |
| Test framework | `test` | Không |

Chỉ để dependency ở scope mặc định khi plugin thực sự sở hữu dependency đó và core
không cung cấp.

`optional` chỉ ngăn dependency được truyền tiếp cho project phụ thuộc artifact này. Nó
không bảo đảm Maven Shade Plugin loại dependency khỏi JAR; dependency chỉ dùng lúc
compile, như Lombok, vẫn nên đặt `provided`.

### 12.3 Shade plugin

Mẫu:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>${maven-shade-plugin.version}</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <shadedArtifactAttached>false</shadedArtifactAttached>
                <createDependencyReducedPom>false</createDependencyReducedPom>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Shade plugin tạo một JAR chứa code plugin và các dependency runtime không phải
`provided`.

Không shade:

- `plugin-api`
- `pf4j` và `pf4j-spring`
- Spring Framework hoặc Spring Boot
- SLF4J và Log4j2 do core quản lý
- MongoDB driver do core quản lý

Nếu bắt buộc dùng một phiên bản thư viện khác core và package có thể xung đột, cân nhắc
Maven Shade relocation. Chỉ relocation khi đã kiểm tra reflection, serialization,
service loader và resource path của thư viện.

### 12.4 Kiểm tra dependency

```bash
mvn dependency:tree
```

Tìm các dấu hiệu:

- Nhiều version của cùng dependency.
- Spring hoặc PF4J xuất hiện trong runtime dependency của plugin.
- Logging provider bị đóng gói vào JAR.
- Dependency core không có scope `provided`.
- Dependency snapshot hoặc version range không kiểm soát.

Kiểm tra JAR:

```bash
jar tf target/<artifactId>-<version>.jar | less
```

Ví dụ các package không nên xuất hiện trong JAR plugin nếu core cung cấp:

```text
org/springframework/
org/pf4j/
org/slf4j/
org/apache/logging/log4j/
com/mongodb/
com/fds/flexdata/pluginapi/
```

## 13. Logging

Plugin dùng logging facade thống nhất với core:

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExampleService {

    public void process(String id) {
        log.info("Processing entity id={}", id);
    }
}
```

Quy tắc:

- Không dùng `System.out` hoặc `System.err`.
- Không khởi tạo logging context riêng.
- Không đóng gói thêm SLF4J provider hoặc Log4j2 implementation nếu core đã cung cấp.
- Dùng placeholder thay vì nối chuỗi.
- Không log token, password, dữ liệu định danh hoặc toàn bộ request nhạy cảm.
- Giữ logger name theo package class để core cấu hình level tập trung.

Format console và file do cấu hình logging của core quyết định. Plugin không nên mang
`log4j2.xml` hoặc `log4j2-spring.xml` riêng trừ khi kiến trúc triển khai đã quy định rõ
cách hợp nhất cấu hình.

## 14. Xử lý lỗi

Ưu tiên exception và response contract trong `plugin-api`:

```java
throw new AppException(
        MessageCode.LOI_DU_LIEU,
        "Dữ liệu không hợp lệ");
```

Quy tắc:

- Dùng mã lỗi chung khi contract đã có.
- Không trả stack trace cho client.
- Không catch `Exception` rồi trả thành công.
- Không đổi lỗi nghiệp vụ thành lỗi hệ thống nếu có thể phân loại.
- Log lỗi ở đúng một tầng để tránh lặp nhiều stack trace.
- Giữ message kỹ thuật trong log và message an toàn trong response.

## 15. Threading và tài nguyên

Plugin có thể inject executor do core cung cấp. Nếu tự tạo thread pool:

- Khai báo thành Spring bean.
- Đặt tên thread có prefix plugin.
- Giới hạn queue và số thread.
- Đóng executor khi plugin dừng.
- Không dùng `new Thread(...)` trong request.

Khi chạy async, các context dạng `ThreadLocal` không tự truyền sang thread mới. Nếu cần
user context, MDC hoặc security context:

1. Capture context tại request thread.
2. Set context trong worker thread.
3. Luôn clear trong khối `finally`.

Ví dụ:

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

Không clear context chỉ ở nhánh thành công, vì exception sẽ làm rò dữ liệu sang task kế
tiếp trong thread pool.

Các resource plugin tự sở hữu phải được giải phóng khi context đóng, ví dụ bằng
`@PreDestroy`, `DisposableBean` hoặc `destroyMethod`.

## 16. Build

Yêu cầu:

- JDK 21.
- Maven tương thích.
- Có quyền truy cập repository chứa các artifact nội bộ FlexData.
- Phiên bản `plugin-api` mục tiêu đã có trong Maven repository.

Build:

```bash
mvn clean package
```

Artifact:

```text
target/<artifactId>-<version>.jar
```

Không chạy:

```bash
java -jar target/<artifactId>-<version>.jar
```

JAR plugin thường không có `Main-Class` và phải được chạy bên trong core.

### 16.1 Checklist artifact

```bash
JAR=target/<artifactId>-<version>.jar

test -f "$JAR"
unzip -p "$JAR" plugin.properties
unzip -p "$JAR" application.yml
unzip -p "$JAR" META-INF/extensions.idx
jar tf "$JAR" | sort
```

Xác nhận:

- Descriptor đúng `plugin.id`, `plugin.version`, `plugin.class`.
- Bootstrap class có trong JAR.
- `META-INF/extensions.idx` chứa đủ extension.
- Không đóng gói dependency shared.
- Không có secret.
- Tên file artifact có version rõ ràng.

## 17. Chạy và triển khai với core

### 17.1 Cấu hình thư mục plugin

Core đọc plugin từ:

```yaml
app:
  plugin:
    plugins-home: plugins
    app-property-file-name: application.yml
```

Core chỉ nạp file JAR hoặc ZIP.

Có thể copy JAR vào thư mục plugin trước khi core khởi động:

```bash
cp target/<artifactId>-<version>.jar <core>/plugins/
```

Hoặc cấu hình `plugins-home` trỏ tới thư mục build khi phát triển local. Không nên dùng
đường dẫn local cố định trong cấu hình production.

### 17.2 Upload và start qua API

Core cung cấp API quản lý plugin:

```text
/api/admin/v1/plugins
```

| Method | Path | Chức năng |
|---|---|---|
| `GET` | `/` | Liệt kê plugin |
| `POST` | `/upload-start` | Upload và start plugin |
| `PATCH` | `/start/{id}` | Start plugin đã load |
| `PATCH` | `/stop/{id}` | Stop plugin |
| `PATCH` | `/disable/{id}` | Disable plugin |
| `PATCH` | `/unload/{id}` | Unload plugin |
| `PATCH` | `/reload/{id}` | Load lại plugin đã unload |
| `DELETE` | `/delete/{id}` | Unload, xóa metadata và file plugin |

Ví dụ:

```bash
curl -X POST \
  "$BASE_URL$CONTEXT_PATH/api/admin/v1/plugins/upload-start" \
  -H "AuthorizationDC: Bearer $TOKEN" \
  -F "file=@target/<artifactId>-<version>.jar" \
  -F "name=<display-name>"
```

`{id}` trong các API quản lý là ID bản ghi plugin do core lưu, không nhất thiết bằng
`plugin.id` trong descriptor.

### 17.3 Vòng đời runtime

Khi core khởi động:

1. Tìm JAR/ZIP trong `plugins-home`.
2. PF4J đọc `plugin.properties`.
3. Load class bằng plugin classloader.
4. Start plugin.
5. Plugin tạo child Spring context.
6. Core inject các extension.
7. Core đăng ký controller endpoint.
8. Core refresh danh sách `HandlerAdvice`.
9. Core cập nhật metadata dịch vụ dữ liệu.

Trạng thái thường gặp:

```text
CREATED -> RESOLVED -> STARTED -> STOPPED
                              -> DISABLED
                              -> UNLOADED
```

Khi stop/unload/delete:

- Endpoint plugin được gỡ.
- Cache `HandlerAdvice` được tạo lại.
- Plugin context và resource phải được đóng đúng cách.

### 17.4 Môi trường nhiều instance

Core có cơ chế phát sự kiện thao tác plugin qua Redis để các instance khác thực hiện
hành động tương ứng.

Khi triển khai nhiều instance:

- Mọi instance phải chạy phiên bản core tương thích.
- Mọi instance phải truy cập được artifact plugin tại đường dẫn mà event tham chiếu,
  hoặc phải có cơ chế phân phối file chung.
- Không thay JAR tại chỗ khi plugin vẫn đang chạy.
- Dùng API quản lý plugin để giữ metadata và trạng thái đồng bộ.
- Kiểm tra endpoint và advice trên tất cả instance sau rollout.

## 18. Kiểm thử

### 18.1 Unit test

Unit test cho:

- Service nghiệp vụ.
- Validation.
- Mapper.
- HandlerAdvice callback.
- Logic xử lý khi `ClientSession` là `null`.
- Logic khi bean core trả dữ liệu rỗng hoặc lỗi.

Mock các interface core, không khởi động toàn bộ core cho unit test.

### 18.2 Context test

Kiểm tra:

- `PluginConfig` scan đủ bean.
- Không trùng bean name.
- Mọi dependency constructor được resolve.
- Configuration bắt buộc fail fast khi thiếu.

### 18.3 Contract test

Contract test phải compile và chạy với đúng:

- Java version.
- `plugin-api` version.
- Spring Boot version.
- PF4J version.

Nên có test xác nhận:

- Bootstrap class tạo được context với mock parent context.
- Controller implement `ExtensionPoint`.
- HandlerAdvice implement đúng contract.
- `HandlerAdviceKey` có namespace, collection, type và ordinal đúng.

### 18.4 Integration test với core

Đây là bước bắt buộc trước production:

1. Build core và plugin bằng bộ version mục tiêu.
2. Start toàn bộ dependency của core.
3. Upload và start JAR.
4. Xác nhận trạng thái plugin là `STARTED`.
5. Gọi endpoint riêng của plugin.
6. Gọi endpoint core có gắn `HandlerAdvice`.
7. Kiểm tra success path và error path.
8. Stop plugin và xác nhận endpoint không còn truy cập được.
9. Start lại plugin và xác nhận endpoint/advice được đăng ký lại.
10. Unload/reload để phát hiện rò thread, classloader hoặc resource.

## 19. Versioning và phát hành

Nên dùng Semantic Versioning:

```text
MAJOR.MINOR.PATCH
```

- `MAJOR`: thay đổi không tương thích với cấu hình, dữ liệu hoặc contract.
- `MINOR`: thêm tính năng tương thích ngược.
- `PATCH`: sửa lỗi tương thích ngược.

Mỗi release phải đồng bộ:

- `pom.xml` project version.
- `plugin.properties` `plugin.version`.
- Tên artifact.
- Changelog riêng của plugin.
- Ma trận phiên bản core tương thích.

Không phát hành lại một JAR khác nội dung nhưng giữ nguyên version. Điều đó làm cache,
điều tra sự cố và rollback không đáng tin cậy.

## 20. Bảo mật

Plugin chạy trong cùng JVM với core và có thể truy cập bean, database hoặc executor của
core. Vì vậy plugin phải được xem là code có đặc quyền cao.

Yêu cầu tối thiểu:

- Chỉ deploy artifact từ nguồn tin cậy.
- Review dependency và lỗ hổng trước khi phát hành.
- Không dùng input request để tạo class name, script hoặc command.
- Validate namespace và collection nếu nhận từ client.
- Không bỏ qua authorization chỉ vì endpoint nằm trong plugin.
- Không ghi secret hoặc dữ liệu nhạy cảm vào log.
- Không đọc file ngoài phạm vi cần thiết.
- Không tạo endpoint quản trị không có kiểm soát quyền.
- Ký hoặc lưu checksum artifact trong quy trình phát hành nếu hạ tầng hỗ trợ.

## 21. Troubleshooting

### 21.1 Plugin không được load

Kiểm tra:

- File có đuôi `.jar` hoặc `.zip`.
- File nằm đúng `app.plugin.plugins-home`.
- `plugin.properties` nằm ở root JAR.
- `plugin.id`, `plugin.class`, `plugin.version` hợp lệ.
- Bootstrap class có trong JAR.
- JAR không bị lỗi hoặc build dở.

Lệnh:

```bash
jar tf target/<artifactId>-<version>.jar | grep -E \
  'plugin.properties|META-INF/extensions.idx|Plugin.class'
```

### 21.2 Plugin load nhưng không start

Nguyên nhân thường gặp:

- Thiếu bean từ parent context.
- Sai version `plugin-api`.
- Dependency shared bị đóng gói trùng.
- Configuration bắt buộc bị thiếu.
- Bean plugin lỗi trong constructor hoặc `@PostConstruct`.
- Component scan sai package.

Tìm exception gốc đầu tiên trong log, không chỉ xem exception wrapper của PF4J.

### 21.3 Controller không có endpoint

Kiểm tra class:

```java
@RestController
@Extension
public class ExampleController implements ExtensionPoint {
}
```

Sau đó kiểm tra `META-INF/extensions.idx`. Nếu class không có trong index, core không
nhận được controller qua `getExtensions`.

Kiểm tra thêm:

- Plugin có trạng thái `STARTED`.
- Mapping không trùng.
- `@RequestMapping` hợp lệ.
- Core đã chạy bước đăng ký endpoint.

### 21.4 HandlerAdvice không chạy

Kiểm tra:

- Có `@Component`.
- Có `@Extension`.
- Có implement cả `HandlerAdvice` và `ExtensionPoint`.
- Có trong `META-INF/extensions.idx`.
- Namespace đúng.
- Collection đúng, kể cả quy tắc tên collection gốc.
- `OpenAPI.Type` đúng operation.
- Plugin đang `STARTED`.
- Core đã refresh `HandlerAdviceHolder`.

Nếu có nhiều advice cùng key, kiểm tra `ordinal` không trùng.

### 21.5 Không inject được bean core

Kiểm tra:

- Plugin context đã gọi `setParent(parentContext)`.
- Core thực sự khai báo bean.
- Plugin inject interface shared, không inject implementation riêng.
- Contract dependency có scope `provided`.
- Core và plugin dùng cùng version contract.
- Không có bản sao interface trong shaded JAR.

### 21.6 NoClassDefFoundError

Hai trường hợp đối lập:

1. Dependency đáng lẽ do core cung cấp nhưng core không có.
2. Dependency riêng của plugin bị đặt nhầm `provided` và không có trong JAR.

Kiểm tra:

```bash
mvn dependency:tree
jar tf target/<artifactId>-<version>.jar
```

### 21.7 ClassCastException hoặc NoSuchMethodError

Thường do:

- Đóng gói trùng `plugin-api`, Spring, PF4J, Jackson hoặc MongoDB.
- Core và plugin dùng version contract khác nhau.
- Thay JAR khi classloader cũ chưa unload.

Xóa dependency trùng, clean build và thực hiện đúng quy trình unload/reload.

### 21.8 Multiple SLF4J providers

Plugin đang đóng gói logging implementation hoặc SLF4J API riêng.

Đưa logging dependency do core cung cấp về scope `provided`, sau đó:

```bash
mvn clean package
jar tf target/<artifactId>-<version>.jar | grep -E \
  'org/slf4j|org/apache/logging/log4j'
```

### 21.9 Property không có giá trị

Kiểm tra:

- Key có đúng prefix `app.plugin.<plugin-id>` không.
- Property đã có trong core/Consul chưa.
- `application.yml` có ở root JAR không.
- Tên file có khớp `app.plugin.app-property-file-name` không.
- Property có bị đọc trong constructor trước lúc core nạp file plugin không.
- Có plugin khác override cùng key không.

### 21.10 Property plugin làm core đổi hành vi

Nguyên nhân là property source của plugin được thêm với độ ưu tiên cao. Xóa các key
toàn cục khỏi plugin và namespace toàn bộ cấu hình:

```text
app.plugin.<plugin-id>.*
```

### 21.11 Plugin dừng nhưng còn thread hoặc resource

Kiểm tra:

- Executor tự tạo đã shutdown chưa.
- Scheduler đã cancel chưa.
- Bean có cleanup hook chưa.
- Static field có giữ ApplicationContext, bean hoặc class plugin không.
- Listener đã unregister chưa.
- ThreadLocal đã clear chưa.

Rò reference tới class plugin sẽ ngăn classloader được garbage collect sau unload.

## 22. Checklist phát triển plugin mới

### Khởi tạo

- [ ] Chọn `plugin.id` duy nhất.
- [ ] Tạo root package riêng.
- [ ] Đồng bộ Java, Spring Boot, PF4J và `plugin-api` với core.
- [ ] Tạo `plugin.properties`.
- [ ] Tạo class kế thừa `SpringPlugin`.
- [ ] Tạo child context với parent context của core.
- [ ] Giới hạn `@ComponentScan` trong package plugin.

### Dependency

- [ ] Đặt contract và thư viện core ở scope `provided`.
- [ ] Không đóng gói Spring, PF4J, MongoDB và logging stack của core.
- [ ] Chỉ shade thư viện plugin thực sự sở hữu.
- [ ] Kiểm tra `mvn dependency:tree`.
- [ ] Kiểm tra package trong JAR.

### Extension

- [ ] Controller có `@RestController`, `@Extension`, `ExtensionPoint`.
- [ ] HandlerAdvice có `@Component`, `@Extension`, `HandlerAdvice`,
  `ExtensionPoint`.
- [ ] Mọi extension có trong `META-INF/extensions.idx`.
- [ ] Endpoint không trùng core hoặc plugin khác.
- [ ] HandlerAdvice key và ordinal không trùng ngoài chủ đích.

### Cấu hình

- [ ] Dùng prefix `app.plugin.<plugin-id>`.
- [ ] Không khai báo key toàn cục trong `application.yml` plugin.
- [ ] Không đóng gói secret.
- [ ] Cấu hình cần lúc tạo bean đã có trong core/Consul.
- [ ] Có validation hoặc fail-fast cho property bắt buộc.

### Chất lượng

- [ ] Có unit test cho business logic.
- [ ] Có test cho success và error path của advice.
- [ ] Async code clear context trong `finally`.
- [ ] Resource plugin được cleanup khi stop/unload.
- [ ] Log không chứa dữ liệu nhạy cảm.

### Phát hành

- [ ] `pom.xml` version khớp `plugin.properties`.
- [ ] `mvn clean package` thành công.
- [ ] Artifact có descriptor, config và extension index.
- [ ] Integration test với đúng core version thành công.
- [ ] Test stop/start/unload/reload thành công.
- [ ] Ghi lại core version tương thích.
- [ ] Có checksum và changelog cho artifact.

## 23. Tài liệu riêng của plugin

Mỗi plugin cụ thể nên bổ sung một tài liệu riêng hoặc mở rộng phần này với:

### Thông tin định danh

```text
Plugin ID:
Tên hiển thị:
Phiên bản:
Đơn vị phát triển:
Core version tương thích:
```

### Phạm vi nghiệp vụ

Mô tả:

- Bài toán plugin giải quyết.
- Dữ liệu plugin sử dụng.
- Quy tắc nghiệp vụ chính.
- Những chức năng không thuộc phạm vi plugin.

### API riêng

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| | | | |

### HandlerAdvice

| Namespace | Collection | Type | Ordinal | Mô tả |
|---|---|---|---:|---|
| | | | | |

### Cấu hình

| Key | Bắt buộc | Mặc định | Nguồn | Mô tả |
|---|---|---|---|---|
| | | | | |

### Dependency core

| Contract/Bean | Mục đích |
|---|---|
| | |

### Runbook

Ghi rõ:

- Cách kiểm tra plugin hoạt động.
- Health check nghiệp vụ.
- Log cần theo dõi.
- Cách rollback.
- Ảnh hưởng khi stop hoặc disable.
- Quy trình xử lý lỗi dữ liệu.

## 24. Nguyên tắc cốt lõi

1. Giao tiếp bằng contract dùng chung.
2. Core cung cấp dependency xuống plugin, không phụ thuộc ngược vào plugin.
3. Bean plugin nằm trong child context; core chỉ gọi lại qua extension đã đăng ký.
4. Không đóng gói trùng thư viện core.
5. Mọi controller và advice phải là PF4J extension.
6. Mọi cấu hình plugin phải có namespace riêng.
7. JAR plugin không phải executable JAR.
8. Build thành công chưa đủ; phải kiểm thử trên core thật.
9. Stop, unload và reload phải giải phóng toàn bộ resource.
10. Phiên bản artifact, descriptor và contract phải được quản lý đồng bộ.
