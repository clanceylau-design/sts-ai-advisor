package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.ShopItemsState;

import java.util.concurrent.CompletableFuture;

/**
 * GetShopItemsTool - 获取商店商品工具
 *
 * <p>获取商店中所有商品的描述和价格
 */
public class GetShopItemsTool implements GameTool {

    @Override
    public String getId() {
        return "get_shop_items";
    }

    @Override
    public String getDescription() {
        return "获取商店中所有商品的描述和价格，包括卡牌、遗物、药水和卡牌移除服务";
    }

    @Override
    public InfoType getInfoType() {
        return InfoType.ON_DEMAND;
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.add("required", new JsonArray());
        return schema;
    }

    @Override
    public boolean isAvailableForScenario(String scenario) {
        return "shop".equals(scenario);
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            System.out.println("[GetShopItemsTool] Executing...");

            try {
                ShopItemsState shopItems = context.getShopItems();

                JsonObject data = new JsonObject();

                // 玩家金币
                data.addProperty("player_gold", shopItems.getPlayerGold());

                // 卡牌商品
                JsonArray cardsArray = new JsonArray();
                for (ShopItemsState.CardItem card : shopItems.getCardItems()) {
                    JsonObject cardObj = new JsonObject();
                    cardObj.addProperty("index", card.getIndex());
                    cardObj.addProperty("name", card.getName());
                    cardObj.addProperty("type", card.getType());
                    cardObj.addProperty("cost", card.getCost());
                    cardObj.addProperty("price", card.getPrice());
                    if (card.getDescription() != null) {
                        cardObj.addProperty("description", card.getDescription());
                    }
                    if (card.isOnSale()) {
                        cardObj.addProperty("on_sale", true);
                        cardObj.addProperty("original_price", card.getOriginalPrice());
                    }
                    cardsArray.add(cardObj);
                }
                data.add("cards", cardsArray);

                // 遗物商品
                JsonArray relicsArray = new JsonArray();
                for (ShopItemsState.RelicItem relic : shopItems.getRelicItems()) {
                    JsonObject relicObj = new JsonObject();
                    relicObj.addProperty("index", relic.getIndex());
                    relicObj.addProperty("name", relic.getName());
                    relicObj.addProperty("price", relic.getPrice());
                    if (relic.getDescription() != null) {
                        relicObj.addProperty("description", relic.getDescription());
                    }
                    relicsArray.add(relicObj);
                }
                data.add("relics", relicsArray);

                // 药水商品
                JsonArray potionsArray = new JsonArray();
                for (ShopItemsState.PotionItem potion : shopItems.getPotionItems()) {
                    JsonObject potionObj = new JsonObject();
                    potionObj.addProperty("index", potion.getIndex());
                    potionObj.addProperty("name", potion.getName());
                    potionObj.addProperty("price", potion.getPrice());
                    if (potion.getDescription() != null) {
                        potionObj.addProperty("description", potion.getDescription());
                    }
                    potionsArray.add(potionObj);
                }
                data.add("potions", potionsArray);

                // 卡牌移除服务
                JsonObject removalObj = new JsonObject();
                removalObj.addProperty("available", shopItems.getCardRemoval().isAvailable());
                removalObj.addProperty("price", shopItems.getCardRemoval().getPrice());
                data.add("card_removal", removalObj);

                System.out.println("[GetShopItemsTool] Success: Cards=" + shopItems.getCardItems().size()
                    + " Relics=" + shopItems.getRelicItems().size()
                    + " Potions=" + shopItems.getPotionItems().size()
                    + " Gold=" + shopItems.getPlayerGold()
                    + " (" + (System.currentTimeMillis() - start) + "ms)");

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                System.err.println("[GetShopItemsTool] Error: " + e.getMessage());
                e.printStackTrace();
                return ToolResult.failure(getId(), "获取商店商品失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}