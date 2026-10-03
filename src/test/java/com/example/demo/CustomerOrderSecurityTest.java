package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerOrderSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void customerCanOnlySeeTheirOwnUnsettledOrdersAfterServerLogin() throws Exception {
        String customerUsername = "orders-" + UUID.randomUUID();
        String otherUsername = "other-" + UUID.randomUUID();
        User customer = createCustomer(customerUsername);
        User otherCustomer = createCustomer(otherUsername);
        Order ownOrder = createOrder(customer, "CF-OWN-" + UUID.randomUUID());
        Order paidOrder = createOrder(customer, "CF-PAID-" + UUID.randomUUID());
        paidOrder.setPaymentStatus("paid");
        orderRepository.save(paidOrder);
        createOrder(otherCustomer, "CF-OTHER-" + UUID.randomUUID());

        MvcResult csrfResult = mockMvc.perform(get("/html/login/login.html"))
                .andExpect(status().isOk())
                .andReturn();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginPayload(customerUsername, "correct horse battery"))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);
        User upgradedCustomer = userRepository.findByUsername(customerUsername);
        assertTrue(upgradedCustomer.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches("correct horse battery", upgradedCustomer.getPasswordHash()));

        MvcResult accountOrders = mockMvc.perform(get("/api/account/orders").session(session))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(get("/api/admin/products").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/account/profile").session(session))
                .andExpect(status().isForbidden());
        JsonNode orders = objectMapper.readTree(accountOrders.getResponse().getContentAsString());
        assertEquals(2, orders.size());
        String resultJson = orders.toString();
        assertTrue(resultJson.contains(ownOrder.getReferenceId()));
        assertTrue(resultJson.contains(paidOrder.getReferenceId()));
        assertTrue(resultJson.contains("\"orderStatus\":\"completed\""));
        assertTrue(resultJson.contains("\"paymentStatus\":\"unpaid\""));
        assertTrue(!resultJson.contains("CF-OTHER-"));

        MvcResult profileResult = mockMvc.perform(get("/api/account/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(customer.getEmail()))
                .andReturn();
        var profileCsrf = profileResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(profileCsrf);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/account/profile")
                        .session(session)
                        .cookie(profileCsrf)
                        .header("X-XSRF-TOKEN", profileCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Updated Customer","phone":"09171112222",
                                 "addressLine":"10 Main Street","city":"Caloocan","landmark":"Near the market"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Customer"))
                .andExpect(jsonPath("$.city").value("Caloocan"));
    }

    @Test
    void customerOrderAndAdminOrderEndpointsRequireTheirRoles() throws Exception {
        mockMvc.perform(get("/api/account/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanLoadProductInventoryManagementList() throws Exception {
        String username = "admin-" + UUID.randomUUID();
        User admin = createCustomer(username);
        admin.setRole("admin");
        userRepository.save(admin);

        MvcResult csrfResult = mockMvc.perform(get("/html/login/login.html"))
                .andExpect(status().isOk())
                .andReturn();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginPayload(username, "correct horse battery"))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/admin/products").session(session))
                .andExpect(status().isOk());
    }

    private User createCustomer(String username) {
        User user = new User();
        user.setFullName("Test Customer");
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("correct horse battery");
        user.setRole("customer");
        return userRepository.save(user);
    }

    private Order createOrder(User customer, String reference) {
        Order order = new Order();
        order.setReferenceId(reference);
        order.setOrderSource("online");
        order.setCustomerName("Test Customer");
        order.setCustomerContact("09170000000");
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerUserId(customer.getId());
        order.setFulfillmentMethod("Storefront Pickup");
        order.setDeliveryFee(BigDecimal.ZERO.setScale(2));
        order.setPaymentMethod("Cash on Pickup / Delivery");
        order.setPaymentStatus("unpaid");
        order.setTotalAmount(new BigDecimal("12.00"));
        order.setOrderStatus("completed");
        return orderRepository.save(order);
    }

    private record LoginPayload(String username, String password) { }
}
