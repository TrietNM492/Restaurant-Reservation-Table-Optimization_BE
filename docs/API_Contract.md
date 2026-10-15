# API Contract

Tài liệu này mô tả các HTTP endpoint được khai báo trong `src/main/java/com/restaurant/ireservation/controller` và DTO tương ứng. Contract phản ánh implementation hiện tại; các endpoint chỉ được nhắc trong security configuration nhưng chưa có controller không được xem là API đã triển khai.

## Quy ước chung

- Base URL local: `http://localhost:8080`; API prefix: `/api/v1`.
- Request/response JSON dùng `Content-Type: application/json`, trừ upload file.
- Các endpoint cần đăng nhập dùng header `Authorization: Bearer <accessToken>`. Quyền cụ thể được ghi ở từng endpoint; nếu không ghi role thì endpoint vẫn cần đăng nhập theo security config.
- Endpoint công khai hiện được cho phép bởi security config: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`; `GET /menus/**`; và một số path whitelist như `/files/**`, `/search/**`, `/ws/**`, `/storage/**`, `/payments/vnpay-ipn`, `/payments/vnpay-return`. Chỉ các endpoint có controller bên dưới được liệt kê.
- Ký hiệu: **Bắt buộc** theo Bean Validation/controller; **Tùy chọn** có thể bỏ qua/null. Giới hạn ghi kèm khi có annotation validation.
- Endpoint hỗ trợ Spring `Pageable` nhận query `page` (mặc định thường là `0` theo Spring), `size`, `sort` (ví dụ `sort=id,desc`). Một số endpoint menu tự xử lý `page` từ `1`; được ghi riêng.
- Thông thường response thành công là HTTP `200`; create thường là `201`; delete có thể trả `204` như ghi dưới đây. Các JSON body thành công (ngoại trừ chuỗi/resource) được bọc:

  ```json
  {
    "timestamp": "2026-01-31T10:15:30",
    "statusCode": 200,
    "error": null,
    "message": null,
    "data": {}
  }
  ```

  `message` có thể chứa nội dung từ annotation `@ApiMessage`. Endpoint phân trang đặt `data` theo dạng:

  ```json
  {
    "meta": {
      "page": 0,
      "pageSize": 10,
      "pages": 1,
      "total": 0,
      "hasNext": false,
      "hasPrevious": false
    },
    "result": []
  }
  ```

- Response kiểu chuỗi và file/resource không qua wrapper. Lỗi HTTP từ 400 trở lên không qua response advice; các lỗi nghiệp vụ được xử lý có cấu trúc thường có `{timestamp, statusCode, error, message, data}` (trong đó `data` thường null). Validation trả `message` là object ánh xạ tên field tới lỗi.
- Ngày giờ: `LocalDate` dùng `YYYY-MM-DD`; `Instant` dùng ISO-8601 UTC; timestamp response dùng ISO-8601 local date-time.

## Authentication và tài khoản

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| `POST` | `/auth/register` | Công khai | JSON `RegisterAccount`: `username` (string, bắt buộc), `email` (string, tùy chọn; nếu có phải hợp lệ), `fullname` (string, bắt buộc), `phone` (string, tùy chọn; nếu có theo mẫu số điện thoại VN), `password` (string, bắt buộc; 8–16 ký tự, phải có chữ hoa, chữ thường, số và ký tự đặc biệt), `passwordAgain` (string, bắt buộc; 8–16 ký tự; phải khớp `password`). | `200 CustomerResponse`. |
| `POST` | `/auth/login` | Công khai | JSON `LoginDTO`: `username` (string, bắt buộc), `password` (string, bắt buộc). | `200 ResLoginDTO`; đồng thời đặt cookie `refresh_token` bảo mật/HTTP-only. |
| `POST` | `/auth/logout` | Cần đăng nhập | Cookie `refresh_token` tùy chọn. | `200` chuỗi `"Logout OK"`; string không được bọc. |
| `POST` | `/auth/refresh` | Công khai; cần cookie | Cookie `refresh_token` bắt buộc. | `200 ResLoginDTO`; làm mới cookie `refresh_token`. |
| `PATCH` | `/users/activated` | `ADMIN` | JSON scalar `Long`, ví dụ `123` (user ID; null bị từ chối). | `200` thông báo dạng chuỗi. |
| `GET` | `/users/me` | `ROLE_CUSTOMER` | Không có. | `200 CustomerResponse`. |
| `PUT` | `/users/me` | `CUSTOMER` | JSON `ChangeCustomerProfileDTO`: `fullname` (string, bắt buộc, không blank), `gender` (string/enum, tùy chọn), `phone` (string, tùy chọn), `indentityCard` (string, tùy chọn), `address` (string, tùy chọn), `dateOfBirth` (date, tùy chọn, phải ở quá khứ). | `200 CustomerResponse`. |
| `POST` | `/users/change-avatar` | `ROLE_CUSTOMER` | Multipart form-data, part `avatarUrl` (file, bắt buộc). | `200` chuỗi `newAvatar: {filename}`; string không được bọc. |

## Đặt bàn, phòng và reservation

### Reservation

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| `POST` | `/reservations` | `ROLE_CUSTOMER` | JSON `CreateReservationRequestDTO`: `partySize` (enum, bắt buộc), `reservationType` (enum, bắt buộc), `startTime` (Instant, bắt buộc và phải ở tương lai), `endTime` (Instant, bắt buộc), `tableIds` (array<Long>, bắt buộc, không rỗng), `note` (string, tùy chọn, tối đa 500 ký tự). | `201 ReservationResponseDTO`. |
| `PUT` | `/reservations/{id}` | `ROLE_CUSTOMER` | Path `id: Long`; body cùng schema và validation với `POST /reservations`. | `200 ReservationResponseDTO`. |
| `POST` | `/reservations/{id}/cancel` | `ROLE_CUSTOMER` | Path `id: Long`; không body. | `200 ReservationResponseDTO`. |
| `POST` | `/reservations/{id}/check-in` | Customer, staff, manager hoặc admin | Path `id: Long`; không body. | `200 ReservationResponseDTO`. |
| `POST` | `/reservations/{id}/complete` | Customer, staff, manager hoặc admin | Path `id: Long`; không body. | `200 ReservationResponseDTO`. |
| `GET` | `/reservations/me` | `ROLE_CUSTOMER` | Không có. | `200 array<ReservationResponseDTO>`. |

### Tra cứu bàn và cập nhật trạng thái

| Method | Endpoint | Quyền | Query/path/body | Response |
|---|---|---|---|---|
| `GET` | `/tables` | Cần đăng nhập | Query tùy chọn: `q: String`, `areaId: Long`, `statusCode: TableStatus`, và pageable. | `200` trang `TableResponse`. |
| `GET` | `/tables/summary` | Cần đăng nhập | Không có. | `200 TableStatusSummaryResponse`. |
| `GET` | `/tables/floor-plan` | Cần đăng nhập | Không có. | `200 array<AreaFloorPlanResponse>`. |
| `GET` | `/tables/area/{areaId}` | Cần đăng nhập | Path `areaId: Long` dương. | `200 array<TableResponse>`. |
| `GET` | `/tables/{id}` | Cần đăng nhập | Path `id: Long` dương. | `200 TableResponse`. |
| `PATCH` | `/tables/{id}/status` | Cần đăng nhập | Path `id: Long` dương; body `ChangeTableStatusRequest`: `statusCode: TableStatus` bắt buộc. Path ID được dùng làm ID bàn. | `200 TableResponse`. |
| `PATCH` | `/tables/status` | Cần đăng nhập | Body `ChangeTableStatusRequest`: `id: Long` tùy chọn, `statusCode: TableStatus` bắt buộc. | `200 TableResponse`. |

### Tra cứu phòng và cập nhật trạng thái

| Method | Endpoint | Quyền | Query/path/body | Response |
|---|---|---|---|---|
| `GET` | `/rooms` | Cần đăng nhập | Query tùy chọn: `q: String`, `roomType: RoomType`, `roomStatus: RoomStatus`, và pageable. | `200` trang `RoomResponse`. |
| `GET` | `/rooms/available` | Cần đăng nhập | Không có. | `200 array<RoomResponse>`. |
| `GET` | `/rooms/type/{roomType}` | Cần đăng nhập | Path `roomType: RoomType`. | `200 array<RoomResponse>`. |
| `GET` | `/rooms/{id}` | Cần đăng nhập | Path `id: Long` dương. | `200 RoomResponse`. |
| `PATCH` | `/rooms/{id}/status` | Cần đăng nhập | Path `id: Long` dương; body `ChangeRoomStatusRequest`: `roomStatus: RoomStatus` bắt buộc. Path ID được dùng làm ID phòng. | `200 RoomResponse`. |
| `PATCH` | `/rooms/status` | Cần đăng nhập | Body `ChangeRoomStatusRequest`: `id: Long` tùy chọn, `roomStatus: RoomStatus` bắt buộc. | `200 RoomResponse`. |

## Đơn hàng

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| `POST` | `/orders/create` | Admin, staff, manager hoặc customer | JSON `CreateOrderRequestDTO`: `tableId: Long`, `reservationId: Long`, `customerInformationId: Long`, `isVipRoom: Boolean` đều tùy chọn; `items` tùy chọn theo DTO, nếu truyền là array các item `{menuItemId: Long (bắt buộc), quantity: Integer (bắt buộc, >= 1)}`. | `201 OrderResponseDTO`. |
| `GET` | `/orders/status` | Customer | Query `orderId: Long` tùy chọn. | `200 OrderResponseDTO`. |
| `POST` | `/order-items` | Admin, staff, manager hoặc customer | JSON `AddOrderItemRequestDTO`: `orderId: Long`, `menuItemId: Long`, `quantity: Integer >= 1` (đều bắt buộc). | `201 OrderItemResponseDTO`. |
| `PUT` | `/order-items/{id}` | Admin, staff, manager hoặc customer | Path `id: Long`; JSON `UpdateOrderItemRequestDTO`: `quantity: Integer >= 0` bắt buộc. | `200 OrderItemResponseDTO`. |
| `DELETE` | `/order-items/{id}` | Admin, staff, manager hoặc customer | Path `id: Long`; không body. | `200`, body rỗng/void theo controller. |

## Menu

### Menu công khai

| Method | Endpoint | Quyền | Query/path | Response |
|---|---|---|---|---|
| `GET` | `/menus/categories` | Công khai | Không có. | `200 array<MenuCategoryPublicResponse>`. |
| `GET` | `/menus` | Công khai | Query tùy chọn: `q: String`, `categoryId: Long`, `minPrice: Double`, `maxPrice: Double`, `sort: String`; `page: int` mặc định `1`, `size: int` mặc định `10`. | `200` phân trang `MenuItemPublicResponse`. |
| `GET` | `/menus/{id}` | Công khai | Path `id: Long`. | `200 MenuItemPublicResponse`. |

### Admin menu category

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| `GET` | `/admin/menu-category` | `ADMIN` | Query `q: String` tùy chọn; pageable mặc định size 10, sort `id,DESC`. | `200` trang `MenuCategoryResponse`. |
| `GET` | `/admin/menu-category/{id}` | `ADMIN` | Path `id: Long`. | `200 MenuCategoryResponse`. |
| `POST` | `/admin/menu-category` | `ADMIN` | JSON `MenuCategoryRequest`: `code` (string bắt buộc, không blank, tối đa 50, chỉ chữ/số/`_`/`-`), `name` (string bắt buộc, không blank, tối đa 100), `description` (string tùy chọn, tối đa 500), `menuQr` và `menuPicture` (string tùy chọn, tối đa 255). | `201 MenuCategoryResponse`. |
| `PUT` | `/admin/menu-category/{id}` | `ADMIN` | Path `id: Long`; body cùng schema với POST. | `200 MenuCategoryResponse`. |
| `DELETE` | `/admin/menu-category/{id}` | `ADMIN` | Path `id: Long`. | `204`, không body. |

### Admin menu item

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| `GET` | `/admin/menu-item` | `ADMIN` | Query tùy chọn `q: String`, `categoryId: Long`, `status: String`; pageable mặc định size 10, sort `id,DESC`. | `200` trang `MenuItemResponse`. |
| `GET` | `/admin/menu-item/{id}` | `ADMIN` | Path `id: Long`. | `200 MenuItemResponse`. |
| `POST` | `/admin/menu-item` | `ADMIN` | JSON `MenuItemRequest`: `categoryId: Long` bắt buộc; `itemName` (string bắt buộc, không blank, tối đa 150); `price: Double` bắt buộc, >= 0; `description` (string tùy chọn, tối đa 500); `itemPicture` (string tùy chọn, tối đa 225); `estimatedPrepMinutes: Integer` tùy chọn, >= 0; `status: String` tùy chọn; `originId: Long` tùy chọn. | `201 MenuItemResponse`. |
| `PUT` | `/admin/menu-item/{id}` | `ADMIN` | Path `id: Long`; body cùng schema với POST. | `200 MenuItemResponse`. |
| `PATCH` | `/admin/menu-item/{id}/status` | `ADMIN` | Path `id: Long`; JSON `MenuItemStatusRequest`: `status: String` bắt buộc, không blank. | `200 MenuItemResponse`. |
| `DELETE` | `/admin/menu-item/{id}` | `ADMIN` | Path `id: Long`; xóa/ẩn mềm theo controller. | `204`, không body. |

## Quản trị bàn, khu vực và phòng

### Admin tables

| Method | Endpoint | Quyền | Request/query | Response |
|---|---|---|---|---|
| `GET` | `/admin/tables` | `ADMIN` | Query tùy chọn `q: String`, `areaId: Long`, `statusCode: TableStatus`; pageable. | `200` trang `TableResponse`. |
| `GET` | `/admin/tables/{id}` | `ADMIN` | Path `id: Long` dương. | `200 TableResponse`. |
| `POST` | `/admin/tables` | `ADMIN` | JSON `TableRequest`: `id: Long` tùy chọn; `tableNumber: Integer` bắt buộc, dương; `areaId: Long` bắt buộc; `capacity: int` bắt buộc, >= 1; `statusCode: TableStatus` tùy chọn; `description: String` tùy chọn, tối đa 500. | `201 TableResponse`. |
| `PUT` | `/admin/tables/{id}` | `ADMIN` | Path `id: Long`; body cùng schema POST; path ID ghi đè body ID. | `200 TableResponse`. |
| `PUT` | `/admin/tables` | `ADMIN` | Body `TableRequest` như POST. | `200 TableResponse`. |
| `PATCH` | `/admin/tables/status` | `ADMIN` | `ChangeTableStatusRequest`: `id: Long` tùy chọn, `statusCode: TableStatus` bắt buộc. | `200 TableResponse`. |
| `PATCH` | `/admin/tables/{id}/toggle-lock` | `ADMIN` | Path `id: Long` dương; không body. | `200 TableResponse`. |
| `DELETE` | `/admin/tables/{id}` | `ADMIN` | Path `id: Long` dương. | `200`, body rỗng/void. |

### Admin table areas

| Method | Endpoint | Quyền | Request/query | Response |
|---|---|---|---|---|
| `GET` | `/admin/table-areas` | `ADMIN` | Query tùy chọn `q: String`, `name: AreaName`; pageable. | `200` trang `TableAreaResponse`. |
| `GET` | `/admin/table-areas/{id}` | `ADMIN` | Path `id: Long` dương. | `200 TableAreaResponse`. |
| `POST` | `/admin/table-areas` | `ADMIN` | JSON `TableAreaRequest`: `id: Long` tùy chọn; `code: String` bắt buộc, không blank, 2–50 ký tự; `name: AreaName` bắt buộc; `description: String` tùy chọn, tối đa 500. | `201 TableAreaResponse`. |
| `PUT` | `/admin/table-areas/{id}` | `ADMIN` | Path `id: Long`; body cùng schema POST; path ID ghi đè body ID. | `200 TableAreaResponse`. |
| `PUT` | `/admin/table-areas` | `ADMIN` | Body `TableAreaRequest` như POST. | `200 TableAreaResponse`. |
| `DELETE` | `/admin/table-areas/{id}` | `ADMIN` | Path `id: Long` dương. | `200`, body rỗng/void. |

### Admin rooms

| Method | Endpoint | Quyền | Request/query | Response |
|---|---|---|---|---|
| `GET` | `/admin/rooms` | `ADMIN` | Query tùy chọn `q: String`, `roomType: RoomType`, `roomStatus: RoomStatus`; pageable. | `200` trang `RoomResponse`. |
| `GET` | `/admin/rooms/{id}` | `ADMIN` | Path `id: Long` dương. | `200 RoomResponse`. |
| `POST` | `/admin/rooms` | `ADMIN` | JSON `RoomRequest`: `id: Long` tùy chọn; `roomName: String` bắt buộc, không blank, 2–100 ký tự; `roomType: RoomType` bắt buộc; `capacity: int` bắt buộc, >= 10; `price: double` bắt buộc, >= 0; `roomStatus: RoomStatus` tùy chọn; `description: String` tùy chọn, tối đa 500. | `201 RoomResponse`. |
| `PUT` | `/admin/rooms/{id}` | `ADMIN` | Path `id: Long`; body cùng schema POST; path ID ghi đè body ID. | `200 RoomResponse`. |
| `PUT` | `/admin/rooms` | `ADMIN` | Body `RoomRequest` như POST. | `200 RoomResponse`. |
| `PATCH` | `/admin/rooms/status` | `ADMIN` | `ChangeRoomStatusRequest`: `id: Long` tùy chọn, `roomStatus: RoomStatus` bắt buộc. | `200 RoomResponse`. |
| `PATCH` | `/admin/rooms/{id}/toggle-maintenance` | `ADMIN` | Path `id: Long` dương; không body. | `200 RoomResponse`. |
| `DELETE` | `/admin/rooms/{id}` | `ADMIN` | Path `id: Long` dương. | `200`, body rỗng/void. |

## Nhân viên, role và permission

### Staff

| Method | Endpoint | Quyền | Request/query | Response |
|---|---|---|---|---|
| `GET` | `/staffs` | Cần đăng nhập | Query `q: String` tùy chọn và pageable. | `200` trang `StaffInformationDTO`. |
| `GET` | `/admin/staffs` | `ADMIN` | Query `q: String` tùy chọn và pageable. | `200` trang `StaffInformationResponse`. |
| `POST` | `/admin/staffs` | `ADMIN` | JSON `StaffInformationRequest` (mở rộng `UserRequestDTO`): `username` (string bắt buộc, không blank), `fullname` (string bắt buộc, không blank), `role` (string bắt buộc, không blank), `email` (string tùy chọn, nếu có phải hợp lệ), `gender: Gender` tùy chọn, `phone: String` tùy chọn, `department: Department` tùy chọn. | `201 StaffInformationResponse`. |
| `GET` | `/admin/staffs/{id}` | `ADMIN` | Path `id: Long` dương. | `200 StaffInformationResponse`. |
| `PUT` | `/admin/staffs` | `ADMIN` | JSON `UpdateUserStaffRequest`: `id: Long` tùy chọn; `fullname` và `role` (string bắt buộc, không blank); `email` (string tùy chọn, nếu có phải hợp lệ); `gender: Gender`, `phone: String`, `department: Department` tùy chọn. | `200 StaffInformationResponse`. |
| `PATCH` | `/admin/staffs/status` | `ADMIN` | JSON `ChangeUserStatus`: `id: Long` bắt buộc, dương; `reason: String` tùy chọn; `notifyUser: boolean` tùy chọn. | `200 ChangeUserStatus`. |

### Roles

| Method | Endpoint | Quyền | Request/query | Response |
|---|---|---|---|---|
| `GET` | `/roles` | `ADMIN` | Query `q: String` tùy chọn và pageable. | `200` trang role. |
| `GET` | `/roles/{id}` | `ADMIN` | Path `id: Long` dương. | `200 RoleResponse`. |
| `POST` | `/roles` | `ADMIN` | JSON `RoleRequest`: `name` (string bắt buộc, không blank, tối đa 50, chỉ chữ/số/`_`/`-`); `permissionIds: array<Long>` bắt buộc, tối đa 1000, mỗi ID dương (có thể là mảng rỗng); `description: String` tùy chọn, tối đa 255. | `201 RoleResponse`. |
| `PUT` | `/roles/{id}` | `ADMIN` | Path `id: Long`; body `RoleRequest` như POST. | `200 RoleResponse`. |
| `PUT` | `/roles/{id}/permissions` | `ADMIN` | Path `id: Long`; JSON `RolePermissionsRequest`: `permissionIds: array<Long>` bắt buộc, tối đa 1000, từng ID dương; mảng rỗng được chấp nhận. | `200 RoleResponse`. |
| `DELETE` | `/roles/{id}` | `ADMIN` | Path `id: Long` dương. | `204`, không body. |

### Permissions

| Method | Endpoint | Quyền | Request/query | Response |
|---|---|---|---|---|
| `GET` | `/permissions/` | `ADMIN` | Query `q: String` tùy chọn và pageable. Mapping khai báo dấu `/` cuối. | `200` trang permission. |
| `POST` | `/permissions` | `ADMIN` | JSON `PermissionRequest`: `id: Long` tùy chọn; `name: String` và `method: String` bắt buộc, không blank; `rolesName: array<String>` bắt buộc, ít nhất một phần tử, mỗi phần tử không blank. | `201 Permission` entity. |
| `PUT` | `/permissions` | `ADMIN` | Body `PermissionRequest` như POST. | `200 Permission` entity. |
| `DELETE` | `/permissions/{id}` | `ADMIN` | Path `id: Long` dương. | `200` chuỗi thông báo. |

## Schema response chính

Các schema sau là object nằm trong `data`, trừ khi endpoint ghi rõ là danh sách/phân trang:

- `ResLoginDTO`: `accessToken: String`, `user: {id, email, name, role, username, avatar}`, `expiresIn`.
- `CustomerResponse`: `user: ResLoginDTO`, `profile: {fullname, gender, phone, dateOfBirth, indentityCard, address, point, avatar, customerRank}`.
- `ReservationResponseDTO`: `id, reservationNo, partySize, reservationType, reservationStatus, startTime, endTime, note, tables[]`; mỗi table có `{id, tableNumber, capacity}`; thêm `createdBy, createdDate`.
- `OrderResponseDTO`: `id, orderNo, tableId, tableNumber, reservationId, reservationNo, customerInformationId, orderStatus, isVipRoom, totalAmount, items, createdBy, createdDate, lastModifiedDate`.
- `OrderItemResponseDTO`: `id, orderId, menuItemId, menuItemName, quantity, unitPrice, subtotal, createdDate`.
- `MenuCategoryPublicResponse`: `id, code, name, description, menuPicture, itemCount`.
- `MenuCategoryResponse`: các field public ở trên và `menuQr, createdDate, lastModifiedDate`.
- `MenuItemPublicResponse`: `id, categoryId, categoryName, itemName, description, price, itemPicture, estimatedPrepMinutes, available, originMadeIn`.
- `MenuItemResponse`: `id, categoryId, categoryName, itemName, description, price, itemPicture, estimatedPrepMinutes, status, originId, originMadeIn, createdDate, lastModifiedDate`.
- `TableResponse`: `id, tableNumber, capacity, statusCode, description, area, createdBy, createdDate, lastModifiedBy, lastModifiedDate`.
- `TableAreaResponse`: `id, code, name, description, createdBy, createdDate, lastModifiedBy, lastModifiedDate`.
- `TableStatusSummaryResponse`: `totalTables, availableTables, reservedTables, inUseTables, cleaningTables, lockedTables, statusCounts, areaSummaries`; mỗi phần tử `areaSummaries` có `areaId, areaCode, areaName, totalTables, availableTables, inUseTables, reservedTables, cleaningTables, lockedTables`.
- `AreaFloorPlanResponse`: `areaId, areaCode, areaName, areaDescription, tables`.
- `RoomResponse`: `id, roomName, roomType, capacity, price, roomStatus, description, createdBy, createdDate, lastModifiedBy, lastModifiedDate`.
- `StaffInformationResponse`: `id, avatar, username, email, fullname, gender, phone, employeeCode, department, salary, joinDate, resignationDate`.
- `StaffInformationDTO` (danh sách `/staffs`): `employeeCode, department, joinDate, resignationDate, tenure, salary, user: {email, gender, fullname, phone, isActive}`.
- `RoleResponse`: `id, code, name, description, createDate, lastModifiedDate, permissions[]`; mỗi permission response có `id, name, method, lastModifiedDate`.
- `Permission` được trả trực tiếp dưới dạng entity trong các endpoint permission create/update; field serialization phụ thuộc entity và quan hệ `roles`.

## Giá trị enum

Request hoặc response enum truyền/nhận bằng chuỗi đúng tên enum:

| Enum | Giá trị |
|---|---|
| `AreaName` | `INDOOR`, `OUTDOOR`, `GARDEN`, `WINDOW` |
| `Department` | `RECEPTION`, `SERVICE`, `KITCHEN`, `MANAGER` |
| `Gender` | `DEFAULT`, `MALE`, `FEMALE`, `OTHER` |
| `ItemStatus` | `AVAILABLE`, `OUT_OF_STOCK`, `ARCHIVED` |
| `OrderStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `AWAITING_PAYMENT`, `OK` |
| `PartySize` | `SINGLE`, `SMALL`, `MEDIUM`, `LARGE`, `XLARGE` |
| `PermissionMethod` | `GET`, `PUT`, `POST`, `PATCH`, `DELETE` |
| `RankCustomer` | `GOLDEN`, `DIAMOND`, `CAOTHU` |
| `ReservationStatus` | `PENDING`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `NO_SHOW`, `CANCELLED` |
| `ReservationType` | `TABLE`, `VIP_ROOM`, `PRIVATE_ROOM`, `WALK_IN`, `EVENT` |
| `RoomStatus` | `AVAILABLE`, `RESERVED`, `PREPARING`, `OCCUPIED`, `CLEANING`, `MAINTENANCE` |
| `RoomType` | `VIP_ROOM`, `PRIVATE_ROOM`, `TERRACE_ROOM` |
| `TableStatus` | `AVAILABLE`, `RESERVED`, `IN_USE`, `CLEANING`, `LOCKED` |

## Endpoint hỗ trợ/không thuộc luồng nghiệp vụ

- `GET /api/v1/hello` có trong `HelloController`, cần đăng nhập theo security config nhưng là endpoint chẩn đoán/demo; không có request body/query, controller tra thông tin security của username hard-code `"CasCas"`. Trả `200 UserSecurityRes` trong response wrapper, với các field `id, username, password, email, gender, fullname, role, permission, sessionId, isActive`. Không dùng làm business API; lưu ý response DTO có field `password`.
- Security config cho phép một số path như `/api/v1/account/**`, `/api/v1/files/**`, `/api/v1/payments/**`, `/api/v1/search/**`, `/api/v1/home` hoặc `/ws/**`; chưa thấy controller mapping tương ứng trong source hiện tại nên không mô tả chúng như endpoint đã triển khai.
