# Lộ trình học Java Backend & xây dự án xin việc (2–3 tháng)

> Dành cho người đã biết backend (FastAPI, PostgreSQL, Docker, pytest) và OOP cơ bản, nay học Java để mở rộng stack và có project đi xin việc.

## Tư duy nền: bạn không bắt đầu từ 0

Bạn đã làm backend thật bằng Python — hiểu REST, dependency injection, ORM, auth, async, testing, Docker. **Phần lớn kiến thức này chuyển thẳng sang Java.** Việc cần làm không phải "học lại backend", mà là học *cách hệ sinh thái Java/Spring làm những thứ bạn đã biết*. Đây là lợi thế lớn: bạn có thể đi nhanh gấp đôi người mới hoàn toàn, và toàn bộ lộ trình dưới đây được thiết kế để tận dụng điều đó.

**Dự án mục tiêu:** E-commerce backend, làm "có chiều sâu" — không phải CRUD giỏ hàng đơn thuần, mà đẩy mạnh phần đặt hàng có transaction + chống bán quá tồn kho khi nhiều người mua cùng lúc. Đây là điểm ăn điểm phỏng vấn nhất.

---

## Phần 1 — Java nền tảng cho backend (Tuần 1–2)

**Mục tiêu:** Đọc/viết Java mạch lạc, hiểu cách project Java được tổ chức và build, sẵn sàng vào Spring mà không khựng vì cú pháp.

### Tuần 1 — Cơ chế ngôn ngữ

1. **Hệ thống kiểu (type system).** Phân biệt *primitive* (`int`, `long`, `double`, `boolean`, `char`) và *reference type* (mọi object, kể cả wrapper `Integer`, `Long`, `Double`). Hiểu autoboxing (`int` ↔ `Integer`) và bẫy kinh điển: so sánh hai `Integer` bằng `==` cho kết quả sai — phải dùng `.equals()`. Dùng `var` cho biến local, nhưng tham số method và field vẫn khai báo kiểu rõ ràng.

2. **Collections Framework — quan trọng nhất tuần này.** Ba nhóm, biết *interface khác implementation*:
   - `List` → `ArrayList` (mặc định, như list Python).
   - `Set` → `HashSet`, `LinkedHashSet` (giữ thứ tự), `TreeSet` (sắp xếp).
   - `Map` → `HashMap` (như dict Python), `LinkedHashMap`, `TreeMap`.
   - Quy tắc nghề: khai báo theo interface, khởi tạo theo implementation — `List<Product> products = new ArrayList<>();`.

3. **Generics.** `List<String>`, `Map<Long, Product>`. Chỉ cần đọc-viết generic cơ bản, chưa cần wildcard (`? extends`, `? super`). Spring Data sẽ bắt bạn viết `JpaRepository<Product, Long>` ngay từ đầu.

4. **Exception: checked vs unchecked — khác Python rõ nhất.**
   - *Checked* (vd `IOException`): bắt buộc `try-catch` hoặc khai báo `throws`.
   - *Unchecked* (`RuntimeException` và con cháu): không bắt buộc.
   - Học `try-catch-finally`, **try-with-resources** (như `with` của Python), và tự tạo exception (`extends RuntimeException`).

### Tuần 2 — Phong cách hiện đại + công cụ

5. **Lambda + Stream API.** Tương đương comprehension / `map` / `filter` của Python. Nắm chuỗi `stream().filter(...).map(...).collect(...)`, cùng `reduce`, `sorted`, `groupingBy`. Code Java backend hiện đại dùng stream dày đặc.

6. **`Optional` — chống NullPointerException.** Spring Data trả `Optional<Product>`. Học `map`, `filter`, `orElse`, `orElseThrow`. Mẫu bạn sẽ viết hàng trăm lần: `repository.findById(id).orElseThrow(() -> new NotFoundException(...))`.

7. **`equals()` và `hashCode()`.** Hiểu vì sao phải override cùng nhau (nếu không, object hành xử sai trong `HashSet`/`HashMap`). Học **record** (Java 14+) — tự sinh constructor, getter, `equals`/`hashCode`, rất hợp làm DTO.

8. **Maven — công cụ build (tương đương pip + requirements.txt).** Bắt buộc nắm trước khi đụng Spring: `pom.xml` (khai báo dependency), vòng đời `compile → test → package`, cách thêm dependency. Khuyên dùng Maven thay Gradle cho người mới: dài dòng hơn nhưng tường minh, và Spring Initializr mặc định ra Maven.

9. **Package + access modifier.** `package`/`import`, bốn mức truy cập (`public`, `private`, `protected`, package-private), bố cục thư mục chuẩn Maven (`src/main/java`, `src/test/java`).

### Nên BỎ QUA
Swing/AWT/JavaFX, applet, threading cấp thấp (`synchronized`, `wait/notify` — để dành Phần 3), nội tại reflection (dùng qua Spring, không tự viết), generic nâng cao (PECS). Chỉ cần "biết nó tồn tại".

### Checkpoint
Viết chương trình quản lý `Product` (id, tên, giá, tồn) trong bộ nhớ, chạy bằng `main()`: thêm sản phẩm, tìm theo id (trả `Optional`), lọc sản phẩm còn hàng và sắp theo giá bằng Stream, ném `ProductNotFoundException` tự định nghĩa khi không thấy. Chạy được bằng Maven. Làm trơn bài này = đủ vốn cú pháp.

---

## Phần 2 — Spring Boot nền tảng (Tuần 3–4)

**Mục tiêu:** Mọi thứ bạn đã biết (REST, DI, ORM) ánh xạ sang cách Spring làm. Cuối giai đoạn dựng được REST API CRUD hoàn chỉnh nối PostgreSQL.

1. **Spring Initializr.** Tạo project tại `start.spring.io` với các dependency: Spring Web, Spring Data JPA, PostgreSQL Driver, Validation, Lombok. Hiểu cấu trúc project sinh ra.

2. **IoC Container & Dependency Injection.** Bạn đã quen `Depends` của FastAPI — đây là phiên bản Spring, mạnh hơn. Học `@Component`, `@Service`, `@Repository`, `@Configuration`, và **constructor injection** (cách inject được khuyến nghị). Hiểu "bean" là gì và container quản lý vòng đời object ra sao.

3. **Spring MVC (REST layer).** `@RestController`, `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`, `@RequestBody`, `@PathVariable`, `@RequestParam`, và `ResponseEntity` để kiểm soát status code. Tương đương router + path/query params bên FastAPI.

4. **Spring Data JPA / Hibernate — phần khác nhiều nhất, đầu tư kỹ.** Đây không giống viết SQL hay SQLAlchemy:
   - `@Entity`, `@Id`, `@GeneratedValue`, mapping cột.
   - Quan hệ: `@ManyToOne`, `@OneToMany`, `@ManyToMany` và `@JoinColumn`.
   - `JpaRepository<Entity, ID>` — có sẵn CRUD.
   - **Derived query methods**: `findByNameContaining`, `findByPriceLessThan`... Spring tự sinh SQL từ tên method.
   - `@Query` cho truy vấn phức tạp (JPQL).
   - Hiểu lazy vs eager loading và cảnh giác **N+1 query problem** (sẽ gặp lại ở Phần 3).

5. **Cấu hình.** `application.properties` / `application.yml`: chuỗi kết nối DB, cổng, JPA settings. Dùng **profile** (`dev`, `prod`) để tách cấu hình.

6. **Bean Validation.** `@Valid` trên `@RequestBody`, cùng `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Email`. Validate đầu vào tự động trước khi vào controller logic.

7. **Xử lý lỗi tập trung.** `@RestControllerAdvice` + `@ExceptionHandler` để biến exception nghiệp vụ thành response JSON gọn (status code + message chuẩn). Thay vì rải `try-catch` khắp nơi.

8. **DTO + kiến trúc phân tầng.** Đừng trả thẳng entity ra API. Tách **DTO** (dùng record) và map thủ công (hoặc MapStruct sau). Kiến trúc chuẩn: **Controller → Service → Repository**. Controller nhận request, Service chứa logic nghiệp vụ, Repository nói chuyện với DB.

### Nên BỎ QUA
Spring WebFlux / reactive (không cần cho fresher), JPA inheritance strategies phức tạp, caching. Để dành.

### Checkpoint
REST API CRUD hoàn chỉnh cho **một** entity (vd `Product`), nối PostgreSQL thật, có validation đầu vào và trả lỗi JSON chuẩn qua `@RestControllerAdvice`, dùng DTO. Test bằng Postman/curl chạy đủ create/read/update/delete.

---

## Phần 3 — Xây lõi dự án (Tuần 5–8)

**Mục tiêu:** Dựng nguyên backend e-commerce, rồi "làm cứng" phần concurrency. **Thứ tự quan trọng: làm xong CRUD trước, sau đó mới quay lại xử lý đồng thời — đừng làm song song.**

### Tuần 5–6: Domain + CRUD

1. **Mô hình hóa domain.** Các entity và quan hệ:
   - `User` (id, email, password hash, role)
   - `Category` ←→ `Product` (`@ManyToOne`)
   - `Product` (id, tên, mô tả, giá, tồn kho)
   - `Cart` ←→ `CartItem` ←→ `Product`
   - `Order` ←→ `OrderItem` ←→ `Product`
   - Vẽ ERD trước khi code.

2. **CRUD đầy đủ** cho Product và Category: tạo, sửa, xóa, xem chi tiết.

3. **Phân trang, lọc, tìm kiếm.** Dùng `Pageable` (Spring tự xử lý `page`, `size`, `sort`), lọc theo category/khoảng giá, tìm theo tên. Đây là thứ mọi e-commerce thật đều có.

4. **Giỏ hàng.** Thêm/sửa/xóa item, tính tổng tiền.

### Tuần 7–8: Phần ăn điểm — đặt hàng an toàn

5. **Luồng đặt hàng với transaction.** Khi user đặt hàng: tạo `Order`, trừ tồn kho, dọn giỏ — tất cả trong **một** `@Transactional`. Nếu một bước fail, rollback toàn bộ. Hiểu rõ `@Transactional` làm gì.

6. **Chống bán quá tồn kho khi nhiều người mua cùng lúc (concurrency) — điểm nhấn của cả dự án.** Đây là chỗ phân biệt bạn với người chỉ copy tutorial:
   - Hiểu vấn đề **race condition**: hai request cùng đọc tồn = 1, cả hai cùng cho đặt → bán âm.
   - **Optimistic locking**: thêm `@Version` vào entity. Khi hai transaction đụng nhau, một cái fail và retry. Hợp với hệ ít tranh chấp.
   - **Pessimistic locking**: `@Lock(PESSIMISTIC_WRITE)` khóa dòng khi đọc. Hợp khi tranh chấp cao.
   - Chọn một cách, hiểu trade-off, và **viết được test chứng minh nó hoạt động** (mô phỏng hai request đồng thời).

7. **(Tùy chọn) Upload ảnh sản phẩm.** Bạn đã dùng MinIO rồi — tích hợp lưu ảnh là điểm cộng, nhưng đừng để nó làm chậm phần lõi.

### Checkpoint
Luồng đặt hàng chạy đúng *và* không bao giờ bán quá tồn khi hai request đến cùng lúc — chứng minh bằng test. Đây là câu chuyện kỹ thuật bạn sẽ kể trong phỏng vấn.

---

## Phần 4 — Security + Testing (Tuần 9–10)

**Mục tiêu:** Biến project từ "đồ án sinh viên" thành "đi làm được".

### Security

1. **Kiến trúc Spring Security.** Hiểu filter chain và cấu hình `SecurityFilterChain` (cách hiện đại, không dùng `WebSecurityConfigurerAdapter` đã lỗi thời).

2. **Xác thực bằng JWT.**
   - Endpoint đăng ký / đăng nhập.
   - Sinh và xác thực JWT token.
   - Một filter đọc token từ header, xác thực mỗi request.

3. **Băm mật khẩu** bằng `BCryptPasswordEncoder`. Không bao giờ lưu mật khẩu thô.

4. **Phân quyền theo role.** `@PreAuthorize` cho customer/admin (vd chỉ admin được tạo/sửa sản phẩm). Bạn đã thiết kế RBAC trước đây nên phần tư duy này nhẹ.

### Testing (lợi thế của bạn — đã quen pytest)

5. **Unit test tầng Service** bằng JUnit 5 + Mockito. Khái niệm mock chuyển thẳng từ pytest. Test logic nghiệp vụ (vd: đặt hàng khi hết tồn phải ném exception).

6. **Integration test** bằng `@SpringBootTest` + `MockMvc` — test cả luồng request → response thật.

7. **(Điểm cộng mạnh) Testcontainers** — chạy test trên PostgreSQL thật trong Docker container thay vì DB giả. Rất ấn tượng với nhà tuyển dụng, và bạn đã rành Docker.

### Checkpoint
Endpoint được bảo vệ đúng, auth JWT hoạt động, phân quyền customer/admin rõ ràng, có test ý nghĩa cho tầng service và vài integration test cho luồng quan trọng.

---

## Phần 5 — Đóng gói để khoe (Tuần 11–12)

**Mục tiêu:** Làm cho người lạ clone về chạy được trong một lệnh, và nhìn vào là thấy chuyên nghiệp.

1. **Docker hóa.** Viết `Dockerfile` (multi-stage build để image gọn) và `docker-compose.yml` gồm app + PostgreSQL. Bạn đã rành phần này. Mục tiêu: `docker compose up` là cả hệ thống chạy.

2. **API documentation.** Tích hợp **Springdoc OpenAPI** → tự sinh Swagger UI. Nhà tuyển dụng mở link là xem được toàn bộ API.

3. **README tử tế.** Đây là thứ tạo ấn tượng đầu tiên: mô tả dự án, **sơ đồ kiến trúc**, hướng dẫn chạy bằng một lệnh, danh sách tính năng, và *đặc biệt* nêu bật điểm kỹ thuật (xử lý concurrency khi đặt hàng).

4. **Deploy thật.** Lên một VPS, hoặc Railway/Render cho nhanh. Có link chạy được ăn đứt project chỉ nằm trên GitHub.

5. **Git sạch sẽ.** Commit có ý nghĩa, dùng branch, có `.gitignore` đúng. Lịch sử commit cũng là thứ nhà tuyển dụng nhìn.

6. **(Điểm cộng) CI cơ bản** bằng GitHub Actions: tự chạy test mỗi lần push. Cho thấy bạn biết quy trình chuyên nghiệp.

### Checkpoint
`docker compose up` chạy toàn bộ, có link deploy hoạt động, Swagger UI xem được, README đọc vào là hiểu và muốn xem code.

---

## Nhà tuyển dụng fresher/intern Java thực sự nhìn gì

Thị trường VN gần như mặc định: **Spring Boot + Spring Data JPA + Spring Security + MySQL/PostgreSQL + Maven/Gradle + Git.** Nhưng cái họ kiểm tra không phải "bạn có làm e-commerce không" (ai cũng có) — mà là **bạn hiểu sâu tới đâu**: tại sao chọn cách map JPA này, `@Transactional` hoạt động ra sao, xử lý race condition thế nào. Một project nhỏ mà bạn giải thích tới tận đáy ăn đứt ba project copy tutorial.

### Cách định vị bản thân khi phỏng vấn

Lợi thế riêng của bạn: nói thật rằng bạn đã làm backend bằng Python (FastAPI, async, hệ thống agentic), có đồ án GraphRAG về luật, nay học thêm Java để mở rộng stack. Điều này cho thấy bạn **học nhanh và hiểu khái niệm độc lập với ngôn ngữ** — đúng thứ nhà tuyển dụng muốn ở fresher. Project Java này chứng minh bạn áp dụng được kiến thức nền sang hệ sinh thái mới, không phải học vẹt một framework.

### Ba câu chuyện kỹ thuật nên chuẩn bị kể
1. Xử lý concurrency khi đặt hàng (optimistic vs pessimistic lock, vì sao chọn cách đó).
2. Cách bạn thiết kế kiến trúc phân tầng và xử lý lỗi tập trung.
3. Cách bạn test — đặc biệt nếu dùng Testcontainers.

---

## Tài nguyên xuyên suốt

- **Baeldung** — trang tham khảo Java/Spring chuẩn mực nhất, tra từng chủ đề.
- **Spring official docs** + **start.spring.io** để tạo project.
- **Java/Spring docs** khi cần chính xác.
- Quan trọng nhất: **gõ code mỗi ngày**. Đọc mà không viết thì mau quên cú pháp.

> Mẹo tiến độ: đánh dấu xong từng Checkpoint trước khi sang phần sau. Đừng nhảy cóc — mỗi phần là nền cho phần kế tiếp.
