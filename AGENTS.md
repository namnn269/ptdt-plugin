# AGENTS.md

## Scope

Các chỉ dẫn này áp dụng cho toàn bộ repository. Đọc `architecture.md` để hiểu lý do
thiết kế và `README.md` để biết hướng dẫn phát triển/build/deploy. Phải đọc phần liên
quan trước khi thay đổi contract, controller, `HandlerAdvice`, dependency hoặc cấu hình.

Trao đổi với người dùng bằng tiếng Việt, trừ khi được yêu cầu khác. Code, package,
class và method dùng tiếng Anh theo quy ước hiện có.

## Project Context

Đây là plugin JAR chạy bên trong FlexData Core qua PF4J và `pf4j-spring`, không phải
Spring Boot application chạy độc lập.

| Thành phần | Giá trị hiện tại |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| PF4J Spring | 0.10.0 |
| FlexData shared libraries | 2.0.0 |
| Root package | `com.fds.flexdata.plugin.ptdt` |
| Build | Maven |

Plugin chỉ được biết contract công khai trong `plugin-api` và các shared library được
khai báo chính thức. Không dùng source hoặc implementation class của core để thiết kế
hay triển khai plugin. Nếu contract hiện có chưa đủ, phải nêu rõ phần contract còn thiếu
thay vì phụ thuộc vào implementation nội bộ.

Các file tích hợp chính:

```text
architecture.md
pom.xml
src/main/resources/plugin.properties
src/main/resources/application.yml
src/main/java/com/fds/flexdata/plugin/ptdt/configuration/PhatTrienDoThiPlugin.java
src/main/java/com/fds/flexdata/plugin/ptdt/configuration/PluginConfig.java
```

## Project Skills

Codex tự khám phá các repository-scoped skill trong `.agents/skills`:

- `$create-handler-advice`: tạo hoặc sửa `HandlerAdvice` cho core CRUD flow.
- `$create-plugin-controller`: tạo hoặc sửa REST controller do plugin sở hữu.

Dùng skill tương ứng khi task khớp phạm vi. Không dùng controller để nhân đôi CRUD flow
đã có trong core; trường hợp đó dùng `HandlerAdvice`.

## Mandatory Workflow

Trước khi sửa:

1. Chạy `git status --short`.
2. Đọc file liên quan và tìm code bằng `rg`/`rg --files`.
3. Xác định thay đổi chưa commit của người dùng.
4. Đối chiếu đúng version `plugin-api` nếu task liên quan contract runtime.

Trong khi sửa:

- Giữ thay đổi nhỏ nhất đủ giải quyết yêu cầu.
- Theo pattern đang có; không refactor hoặc format file không liên quan.
- Không revert, xóa hoặc ghi đè thay đổi của người dùng.
- Không sửa file sinh ra trong `target/`.
- Không tạo commit nếu người dùng không yêu cầu.
- Không dùng lệnh phá hủy như `git reset --hard` hoặc `git checkout --`.

Trước khi kết thúc:

1. Chạy test/build phù hợp.
2. Chạy `git diff --check`.
3. Xem lại diff và báo rõ phần chưa kiểm tra được.

## Architecture Rules

Chiều phụ thuộc hợp lệ:

```text
plugin -> plugin-api/shared contracts <- core
plugin -> interface/bean do core cung cấp
core   -> PF4J ExtensionPoint <- plugin implementation
```

Không được:

- Thêm dependency ngược từ core sang plugin.
- Yêu cầu core import class chỉ tồn tại trong plugin.
- Copy class từ core hoặc `plugin-api` vào plugin.
- Phụ thuộc implementation nội bộ của core khi đã có shared interface.
- Phụ thuộc implementation của plugin khác.
- Dùng reflection để thay thế một contract shared chính thức.

Nếu cần contract mới, contract phải nằm trong module shared; core implement và plugin
inject interface đó.

Plugin Spring context phải tiếp tục:

```java
context.setParent(parentContext);
context.setClassLoader(pluginWrapper.getPluginClassLoader());
context.register(PluginConfig.class);
context.refresh();
```

`@ComponentScan` chỉ scan root package của plugin. Không mở rộng sang package core.

## PF4J Extensions

Controller plugin phải có đủ:

```java
@RestController
@Extension
public class ExampleController implements ExtensionPoint {
}
```

Yêu cầu với controller:

- Constructor injection.
- Mọi endpoint dùng `Constants.API_PREFIX`; giá trị prefix phải bắt đầu bằng `/plugin`.
- Endpoint có version rõ ràng và không trùng route đã công bố.
- Jakarta Validation cho request khi phù hợp.
- Business logic nằm trong service, không nằm trong controller.
- Chỉ dùng security/data-access contract được công bố trong `plugin-api`.

`HandlerAdvice` phải có đủ:

```java
@Component
@Extension
public class ExampleHandlerAdvice
        implements HandlerAdvice, ExtensionPoint {
}
```

Yêu cầu với advice:

- `HandlerAdviceKey` phải đúng namespace, collection và `OpenAPI.Type`.
- Tìm toàn repository trước khi chọn `ordinal`.
- Các advice cùng key phải có `ordinal` khác nhau.
- Không dựa vào thứ tự component scan.
- `ClientSession` có thể là `null`.
- Không đóng Mongo resource do core truyền vào.

Core gom advice theo namespace, collection đã normalize và `OpenAPI.Type`, sau đó dùng
`TreeSet` sắp xếp theo `ordinal`. Trùng ordinal có thể làm mất một advice.

Sau khi thêm hoặc đổi extension, class phải xuất hiện trong:

```text
META-INF/extensions.idx
```

Không sửa extension index thủ công.

## Dependencies and Classloading

Dependency do core cung cấp phải dùng scope `provided`:

- PF4J và `pf4j-spring`
- `plugin-api` và module FlexData shared
- Spring/Spring Boot starter
- MongoDB driver và Jakarta Validation
- SLF4J/Log4j2
- Dependency chỉ dùng lúc compile, ví dụ Lombok

Không shade các package:

```text
org/springframework/
org/pf4j/
org/slf4j/
org/apache/logging/log4j/
com/mongodb/
com/fds/flexdata/pluginapi/
lombok/
```

Chỉ đóng gói dependency mà plugin thực sự sở hữu và core không cung cấp. `optional`
không làm Maven Shade Plugin tự loại dependency khỏi JAR; dependency compile-only vẫn
phải dùng `provided`.

Không nâng Java, Spring Boot, PF4J hoặc `plugin-api` riêng lẻ khi chưa đối chiếu core.

## Configuration

Property riêng của plugin phải dùng prefix:

```text
app.plugin.ptdt.*
```

Không thêm key toàn cục như `server.*`, `logging.*`, `management.*`,
`spring.datasource.*` hoặc `spring.data.mongodb.*` vào config plugin. Core thêm property
source của plugin với độ ưu tiên cao, nên key chung có thể override toàn ứng dụng.

Core hiện nạp `application.yml` trong JAR sau khi gọi start plugin. Vì vậy:

- Property cần trong constructor phải đến từ core, Consul hoặc environment.
- Không giả định property chỉ nằm trong JAR đã sẵn sàng lúc tạo bean.
- Dùng `getRequiredProperty` cho config bắt buộc.
- Chỉ dùng default value khi default đó an toàn.

Không commit secret, token, private key hoặc credential.

`plugin.properties` phải dùng UTF-8, nằm ở root JAR và có:

- `plugin.id` duy nhất.
- `plugin.class` trỏ đúng bootstrap class.
- `plugin.version` khớp project version trong `pom.xml`.

## Java Conventions

- Constructor injection; không field injection.
- Ưu tiên immutable field với `final`; không dùng static mutable state.
- Không giữ static reference tới bean hoặc `ApplicationContext`.
- Controller chỉ điều phối; tách validation/policy phức tạp sang service.
- Không catch `Exception` nếu không xử lý có ý nghĩa; không nuốt lỗi.
- Dùng `AppException`, `MessageCode` và error contract từ `plugin-api`.
- Dùng Mongo/BSON API thay vì ghép query bằng chuỗi.
- Tái sử dụng `ClientSession` của core; không tự mở transaction khi cần tham gia
  transaction hiện tại.

Repository dùng Jackson 3:

```java
tools.jackson.*
```

Không trộn với `com.fasterxml.jackson.*` nếu chưa xác minh contract/classloader.

## Logging, Async and Resources

- Dùng SLF4J/`@Slf4j`; không dùng `System.out` hoặc `System.err`.
- Dùng placeholder, không nối chuỗi chỉ để log.
- Không log secret, authorization header hoặc dữ liệu cá nhân nhạy cảm.
- Không thêm logging config/implementation riêng vào plugin.

Khi chạy async:

- Ưu tiên executor do core cung cấp.
- Capture và restore user/MDC context rõ ràng.
- Luôn clear `ThreadLocal`, user context và MDC trong `finally`.
- Test cả success và exception path.

Resource do plugin tạo phải được đóng khi stop/unload bằng lifecycle hook phù hợp.
Không để thread, scheduler, listener hoặc static reference giữ plugin classloader.

## Validation Commands

Thay đổi tài liệu:

```bash
git diff --check
```

Thay đổi Java:

```bash
mvn test
mvn clean package
git diff --check
```

Sau khi build, xác định artifact:

```bash
JAR="$(find target -maxdepth 1 -type f \
  -name 'ptdt-plugin-*.jar' ! -name 'original-*' | sort | tail -n 1)"
test -n "$JAR"
```

Thay đổi controller hoặc `HandlerAdvice`:

```bash
unzip -p "$JAR" META-INF/extensions.idx
```

Thay đổi descriptor:

```bash
unzip -p "$JAR" plugin.properties
```

Thay đổi dependency hoặc bootstrap:

```bash
mvn dependency:tree
jar tf "$JAR" | \
  grep -E '^(org/springframework|org/pf4j|org/slf4j|org/apache/logging/log4j|com/mongodb|com/fds/flexdata/pluginapi|lombok)/'
```

Nếu `grep` tìm thấy shared package, phải xác minh và loại khỏi JAR nếu core cung cấp.

Thay đổi contract core/plugin cần integration test tối thiểu:

1. Load/start plugin thành công.
2. Endpoint và advice được đăng ký đúng.
3. Stop gỡ endpoint/advice.
4. Start lại hoạt động.
5. Unload/reload không rò resource.

Nếu thiếu hạ tầng để chạy integration test, báo rõ rủi ro còn lại.

## Documentation

Cập nhật `README.md` khi thay đổi:

- Public API.
- Property/configuration.
- `HandlerAdvice`.
- Dependency hoặc yêu cầu version core.
- Quy trình build/deploy.
- Breaking change hoặc migration.

Chi tiết nghiệp vụ riêng đặt trong phần tài liệu riêng, không trộn vào hướng dẫn chung.
Nếu thay đổi ranh giới module, lifecycle, dependency direction, extension mechanism,
transaction, async context hoặc config loading, phải cập nhật cả `architecture.md`.

## Known Baseline Issues

Không coi các vấn đề sau là hành vi mong muốn:

- `pom.xml` là `2.0.0` nhưng `plugin.properties` đang là `1.0.0`.
- Comment trong `plugin.properties` đang lỗi encoding.
- Logging stack đang bị shade vì dependency Log4j2 chưa là `provided`.
- Lombok đang bị shade vì chưa có scope `provided`.
- Repository chưa có source test trong `src/test`.

Không tự sửa các vấn đề ngoài phạm vi task. Nếu task chạm đúng file/cơ chế liên quan,
hãy sửa trong cùng phạm vi hoặc báo rõ cho người dùng; không làm vấn đề nghiêm trọng
hơn.

## Definition of Done

Task hoàn thành khi:

- Yêu cầu được triển khai đầy đủ, không ghi đè thay đổi ngoài phạm vi.
- Ranh giới core/plugin và classloader được giữ đúng.
- Test/build phù hợp thành công.
- Extension/config/dependency artifact đã được kiểm tra khi liên quan.
- `git diff --check` sạch.
- `README.md` được cập nhật nếu hành vi public thay đổi.
- Kết quả cuối nêu file đã sửa, lệnh xác minh và rủi ro chưa kiểm tra được.
