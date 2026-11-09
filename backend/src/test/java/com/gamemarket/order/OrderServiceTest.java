package com.gamemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamemarket.ApiTestBase;
import com.gamemarket.cart.CartItem;
import com.gamemarket.cart.CartRepository;
import com.gamemarket.game.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class OrderServiceTest extends ApiTestBase {

    @MockitoSpyBean
    CartRepository cart;

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderService orderService;

    @AfterEach
    void resetSpy() {
        Mockito.reset(cart);
    }

    private Long userId(MockHttpSession s) throws Exception {
        String body = mvc.perform(get("/api/auth/me").session(s)).andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void checkout_emptyCart_returns400_andCreatesNoOrder() throws Exception {
        MockHttpSession s = newUserSession();
        mvc.perform(post("/api/orders").session(s)).andExpect(status().isBadRequest());
        assertThat(orders.findByUserIdOrderByIdDesc(userId(s))).isEmpty();
    }

    @Test
    void checkout_movesGamesToLibrary_andClearsCart() throws Exception {
        MockHttpSession s = newUserSession();
        Game a = paidGames().get(0);
        Game b = paidGames().get(1);
        mvc.perform(post("/api/cart/{id}", a.getId()).session(s)).andExpect(status().isCreated());
        mvc.perform(post("/api/cart/{id}", b.getId()).session(s)).andExpect(status().isCreated());

        mvc.perform(post("/api/orders").session(s))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalKrw", is(a.getPriceKrw() + b.getPriceKrw())));

        mvc.perform(get("/api/library").session(s)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/cart").session(s)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void checkout_alreadyOwned_returns409() throws Exception {
        MockHttpSession s = newUserSession();
        Game a = paidGames().get(0);
        mvc.perform(post("/api/cart/{id}", a.getId()).session(s)).andExpect(status().isCreated());
        mvc.perform(post("/api/orders").session(s)).andExpect(status().isCreated());

        cart.save(new CartItem(userId(s), a)); // bypasses the cart's own ownership check
        mvc.perform(post("/api/orders").session(s)).andExpect(status().isConflict());
        assertThat(orders.findByUserIdOrderByIdDesc(userId(s))).hasSize(1);
    }

    @Test
    void add_alreadyOwned_returns409() throws Exception {
        MockHttpSession s = newUserSession();
        Game a = paidGames().get(0);
        mvc.perform(post("/api/cart/{id}", a.getId()).session(s)).andExpect(status().isCreated());
        mvc.perform(post("/api/orders").session(s)).andExpect(status().isCreated());
        mvc.perform(post("/api/cart/{id}", a.getId()).session(s)).andExpect(status().isConflict());
    }

    @Test
    void checkout_failureMidway_rollsBack() throws Exception {
        MockHttpSession s = newUserSession();
        Long uid = userId(s);
        Game a = paidGames().get(0);
        mvc.perform(post("/api/cart/{id}", a.getId()).session(s)).andExpect(status().isCreated());
        doThrow(new IllegalStateException("boom")).when(cart).deleteByUserId(anyLong());

        assertThatThrownBy(() -> orderService.checkout(uid)).isInstanceOf(IllegalStateException.class);

        assertThat(orders.findByUserIdOrderByIdDesc(uid)).isEmpty();
        Mockito.reset(cart);
        mvc.perform(get("/api/library").session(s)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/cart").session(s)).andExpect(jsonPath("$.items", hasSize(1)));
    }
}
