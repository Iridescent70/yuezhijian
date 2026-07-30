package com.yuezhijian.server.inventory;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InventoryNumberGenerator {
    public String giftNo() { return "GFT" + token(); }
    public String transferNo() { return "TRF" + token(); }
    public String countNo() { return "CNT" + token(); }
    public String ledgerNo() { return "INV" + token(); }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
