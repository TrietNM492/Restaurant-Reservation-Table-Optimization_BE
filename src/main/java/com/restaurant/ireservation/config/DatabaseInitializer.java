package com.restaurant.ireservation.config;

import com.restaurant.ireservation.domain.document.OccupancyStatistic;
import com.restaurant.ireservation.domain.document.PreparationTimeLog;
import com.restaurant.ireservation.domain.document.ReservationForecast;
import com.restaurant.ireservation.domain.document.WaitingTimeLog;
import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.*;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.*;
import com.restaurant.ireservation.security.AuthoritiesConstants;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    final UserRepository userRepository;
    final StaffInformationRepository staffProfileRepository;
    final PermissionRepository permissionRepository;
    final RoleRepository roleRepository;
    final PasswordEncoder passwordEncoder;
    final WaitingTimeLogRepository waitingTimeLogRepository;
    final PreparationTimeLogRepository preparationTimeLogRepository;
    final OccupancyStatisticRepository occupancyStatisticRepository;
    final ReservationForecastRepository reservationForecastRepository;
    final TableAreaRepository tableAreaRepository;
    final RestaurantTableRepository restaurantTableRepository;
    final MenuCategoryRepository menuCategoryRepository;
    final MenuItemRepository menuItemRepository;
    final FoodOriginRepository foodOriginRepository;
    final OrderRepository orderRepository;
    final OrderItemRepository orderItemRepository;
    final KitchenOrderRepository kitchenOrderRepository;
    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> START INIT DATABASE <<<");
        Long permissionCnt = this.permissionRepository.count();
        Long userCnt = this.userRepository.count();
        Long roleCnt = this.roleRepository.count();

        if (permissionCnt == 0) {
            Set<Permission> permissionList = new HashSet<>();
            permissionList.add(new Permission("CREATE_PERMISSION", PermissionMethod.POST));
            permissionList.add(
                    new Permission("UPDATE_PERMISSION",
                            PermissionMethod.PUT));
            permissionList
                    .add(new Permission("DELETE_PERMISSION", PermissionMethod.DELETE));
            permissionList.add(
                    new Permission("GET_PERMISSION",
                            PermissionMethod.GET));
            permissionList.add(new Permission("GET_ALL_PERMISSION",
                    PermissionMethod.GET));
            permissionList.add(new Permission("GET_ROLE", PermissionMethod.GET));
            permissionList.add(new Permission("GET_ALL_ROLE", PermissionMethod.GET));
            permissionList.add(
                    new Permission("CREATE_ROLE",
                            PermissionMethod.POST));
            permissionList.add(
                    new Permission("UPDATE_ROLE",
                            PermissionMethod.PUT));
            permissionList.add(new Permission("UPDATE_ROLE_PERMISSIONS", PermissionMethod.PUT));
            permissionList.add(new Permission("DELETE_ROLE", PermissionMethod.DELETE));
            this.permissionRepository.saveAll(permissionList);
        }

        if (roleCnt == 0) {
            var permissions = new HashSet<>(this.permissionRepository.findAll());
            var roleAdmin = new Role();
            var roles = new ArrayList<Role>();
            roleAdmin.setName(AuthoritiesConstants.ADMIN);
            roleAdmin.setCode("AD490");
            roleAdmin.setDescription("SUPER ADMIN HAS FULL PERMISSIONS");
            roleAdmin.setPermissions(permissions);
            roleAdmin.setCreatedBy("SYSTEM");
            var roleUser = new Role();
            roleUser.setName(AuthoritiesConstants.CUSTOMER);
            roleUser.setCode("CUS101");
            roleUser.setDescription("CUSTOMER USE APP");
            roleUser.setPermissions(permissions);
            roleUser.setCreatedBy("SYSTEM");
            roles.add(roleUser);
            roles.add(roleAdmin);
            this.roleRepository.saveAll(roles);
        }
        if (userCnt == 0) {
            User admin = new User();
            admin.setFullname("Castorice");
            admin.setActive(true);
            admin.setGender(Gender.MALE);
            admin.setPhone("0912312312");
            admin.setEmail("CasCake@gmail.com".toLowerCase());
            var roleAdmin = this.roleRepository.findOneByNameIgnoreCase(AuthoritiesConstants.ADMIN)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found!"));
            admin.setRole(roleAdmin);
            admin.setPassword(this.passwordEncoder.encode("123123"));
            admin.setUsername("cascas".toLowerCase());
            admin.setCreatedBy("SYSTEM");
            userRepository.save(admin);
            StaffInformation information = new StaffInformation();
            information.setUser(admin);
            information.setEmployeeCode("CasCom");
            information.setDepartment(Department.MANAGER);
            information.setJoinDate(LocalDate.now());
            information.setResignationDate(null);
            information.setSalary(33550336);
            staffProfileRepository.save(information);
        }
        if (permissionCnt != 0 && roleCnt != 0 && userCnt != 0) {
            System.out.println(">>> SKIP PROCESSING INITIALIZE <<<");
        } else {
            System.out.println(">>> INIT DATABASE SUCCESSFULLY");
        }

        initializeMongoData();
        initializeOrderData();
    }

    private void initializeMongoData() {
        Instant now = Instant.now();

        if (waitingTimeLogRepository.count() == 0) {
            waitingTimeLogRepository.save(WaitingTimeLog.builder()
                    .id("sample-waiting-time-001")
                    .reservationId(1L)
                    .estimatedWaitMinutes(15)
                    .actualWaitMinutes(18)
                    .createAt(now.minusSeconds(86_400))
                    .updateAt(now.minusSeconds(86_400))
                    .build());
        }

        if (preparationTimeLogRepository.count() == 0) {
            preparationTimeLogRepository.save(PreparationTimeLog.builder()
                    .id("sample-preparation-time-001")
                    .menuItemId(1L)
                    .estimateMinutes(20)
                    .actualMinutes(23)
                    .createAt(now.minusSeconds(86_400))
                    .updateAt(now.minusSeconds(86_400))
                    .build());
        }

        if (occupancyStatisticRepository.count() == 0) {
            occupancyStatisticRepository.save(OccupancyStatistic.builder()
                    .id("sample-occupancy-001")
                    .businessDate(now)
                    .hourSlot(now.atZone(ZoneId.systemDefault()).getHour())
                    .occupiedTables(12)
                    .availableTables(8)
                    .occupancyRate(0.6)
                    .createAt(now)
                    .updateAt(now)
                    .build());
        }

        if (reservationForecastRepository.count() == 0) {
            reservationForecastRepository.save(ReservationForecast.builder()
                    .id("sample-forecast-001")
                    .forecastDate(now.plusSeconds(86_400))
                    .hourSlot(19)
                    .expectedReservations(10)
                    .expectedOccupancy(16)
                    .generateAt(now)
                    .build());
        }
    }

    private void initializeOrderData() {
        Instant now = Instant.now();

        // Initialize Table Areas
        if (tableAreaRepository.count() == 0) {
            TableArea indoorArea = markSystemCreated(TableArea.builder()
                    .code("INDOOR")
                    .name(AreaName.INDOOR)
                    .description("Indoor seating area")
                    .build());
            TableArea outdoorArea = markSystemCreated(TableArea.builder()
                    .code("OUTDOOR")
                    .name(AreaName.OUTDOOR)
                    .description("Outdoor seating area")
                    .build());
            tableAreaRepository.saveAll(List.of(indoorArea, outdoorArea));
        }

        // Initialize Restaurant Tables
        if (restaurantTableRepository.count() == 0) {
            TableArea indoorArea = tableAreaRepository.findByCode("INDOOR").orElse(null);
            TableArea outdoorArea = tableAreaRepository.findByCode("OUTDOOR").orElse(null);

            List<RestaurantTable> tables = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                RestaurantTable table = markSystemCreated(RestaurantTable.builder()
                        .tableNumber(i)
                        .capacity(4)
                        .area(i <= 6 ? indoorArea : outdoorArea)
                        .statusCode(TableStatus.AVAILABLE)
                        .description("Table " + i)
                        .build());
                tables.add(table);
            }
            restaurantTableRepository.saveAll(tables);
        }

        // Initialize Food Origin
        if (foodOriginRepository.count() == 0) {
            FoodOrigin vietnam = markSystemCreated(FoodOrigin.builder()
                    .madeIn("Vietnam")
                    .description("Local ingredients")
                    .isPeakSeason(true)
                    .discountRate(10)
                    .build());
            foodOriginRepository.save(vietnam);
        }

        // Initialize Menu Categories
        if (menuCategoryRepository.count() == 0) {
            MenuCategory appetizer = markSystemCreated(MenuCategory.builder()
                    .code("APP")
                    .name("Appetizers")
                    .description("Starters and appetizers")
                    .build());
            MenuCategory mainCourse = markSystemCreated(MenuCategory.builder()
                    .code("MAIN")
                    .name("Main Courses")
                    .description("Main dishes")
                    .build());
            MenuCategory dessert = markSystemCreated(MenuCategory.builder()
                    .code("DES")
                    .name("Desserts")
                    .description("Sweet treats")
                    .build());
            menuCategoryRepository.saveAll(List.of(appetizer, mainCourse, dessert));
        }

        // Initialize Menu Items
        if (menuItemRepository.count() == 0) {
            FoodOrigin origin = foodOriginRepository.findAll().stream().findFirst().orElse(null);
            List<MenuCategory> categories = menuCategoryRepository.findAll();
            MenuCategory appetizer = categories.stream().filter(c -> "APP".equals(c.getCode())).findFirst().orElse(null);
            MenuCategory mainCourse = categories.stream().filter(c -> "MAIN".equals(c.getCode())).findFirst().orElse(null);
            MenuCategory dessert = categories.stream().filter(c -> "DES".equals(c.getCode())).findFirst().orElse(null);

            List<MenuItem> menuItems = new ArrayList<>();

            // Appetizers
            menuItems.add(markSystemCreated(MenuItem.builder()
                    .category(appetizer)
                    .itemName("Spring Rolls")
                    .description("Crispy spring rolls with vegetables")
                    .price(50000.0)
                    .estimatedPrepTime(Duration.ofMinutes(15))
                    .statusCode(ItemStatus.AVAILABLE)
                    .origin(origin)
                    .build()));

            menuItems.add(markSystemCreated(MenuItem.builder()
                    .category(appetizer)
                    .itemName("Salad")
                    .description("Fresh garden salad")
                    .price(45000.0)
                    .estimatedPrepTime(Duration.ofMinutes(10))
                    .statusCode(ItemStatus.AVAILABLE)
                    .origin(origin)
                    .build()));

            // Main Courses
            menuItems.add(MenuItem.builder()
                    .category(mainCourse)
                    .itemName("Grilled Chicken")
                    .description("Grilled chicken with herbs")
                    .price(120000.0)
                    .estimatedPrepTime(Duration.ofMinutes(25))
                    .statusCode(ItemStatus.AVAILABLE)
                    .origin(origin)

                    .build());

            menuItems.add(MenuItem.builder()
                    .category(mainCourse)
                    .itemName("Beef Steak")
                    .description("Premium beef steak")
                    .price(180000.0)
                    .estimatedPrepTime(Duration.ofMinutes(30))
                    .statusCode(ItemStatus.AVAILABLE)
                    .origin(origin)

                    .build());

            // Desserts
            menuItems.add(markSystemCreated(MenuItem.builder()
                    .category(dessert)
                    .itemName("Ice Cream")
                    .description("Vanilla ice cream")
                    .price(35000.0)
                    .estimatedPrepTime(Duration.ofMinutes(5))
                    .statusCode(ItemStatus.AVAILABLE)
                    .origin(origin)
                    .build()));

            menuItemRepository.saveAll(menuItems);
        }

        // Initialize Orders
        if (orderRepository.count() == 0) {
            User adminUser = userRepository.findUserByUsername("cascas").orElse(null);
            List<MenuItem> menuItems = menuItemRepository.findAll();

            Order order = Order.builder()
                    .orderNo("ORD-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy")) + "-001")
                    .orderStatus(OrderStatus.PENDING)
                    .totalAmount(0.0)
                    .build();
            order.setCreatedBy(adminUser != null ? adminUser.getUsername() : "SYSTEM");
            Order savedOrder = orderRepository.save(order);

            // Create Order Items
            List<OrderItem> orderItems = new ArrayList<>();
            double totalAmount = 0.0;

            for (int i = 0; i < 2 && i < menuItems.size(); i++) {
                MenuItem menuItem = menuItems.get(i);
                int quantity = 2;
                double subtotal = menuItem.getPrice() * quantity;
                totalAmount += subtotal;

                OrderItem orderItem = OrderItem.builder()
                        .order(savedOrder)
                        .menuItem(menuItem)
                        .quantity(quantity)
                        .unitPrice("VND")
                        .subtotal(subtotal)

                        .build();
                orderItems.add(orderItem);
            }

            orderItemRepository.saveAll(orderItems);

            // Update order total
            savedOrder.setTotalAmount(totalAmount);
            orderRepository.save(savedOrder);

        }

        // Seed a kitchen order independently of whether the order already existed.
        if (kitchenOrderRepository.count() == 0) {
            orderRepository.findAll().stream().findFirst().ifPresent(order -> {
                KitchenOrder kitchenOrder = KitchenOrder.builder()
                        .order(order)
                        .status(OrderKitchenStatus.PENDING)
                        .priority(1)
                        .estimatedPrepTimeMinutes(30)
                        .estimatedReadyTime(now.plusSeconds(1800))
                        .note("Customer requested less spicy")
                        .kitchenStation("GRILL")
                        .build();
                kitchenOrderRepository.save(kitchenOrder);
            });
        }
    }

    private <T extends BaseEntity> T markSystemCreated(T entity) {
        entity.setCreatedBy("SYSTEM");
        return entity;
    }
}
