package cn.popcraft.villagerpro.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationTransactionsTest {
    @Test
    void caravanDispatchAndClaimAreAtomicAndSingleUse() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity) VALUES (1, 20)");
            statement.execute("INSERT INTO warehouse(village_id, item_type, amount) VALUES (1, 'BREAD', 40)");

            assertTrue(OperationTransactions.dispatchCaravan(connection, 1,
                    "BREAD", 16, 4, .15, "capital", "EMERALD", 6,
                    true, 1, 2, 1));
            assertEquals(24, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'BREAD'"));
            assertEquals(1, queryInt(statement, "SELECT COUNT(*) FROM caravan_routes"));

            assertFalse(OperationTransactions.dispatchCaravan(connection, 1,
                    "BREAD", 5, 4, .50, "capital", "EMERALD", 2,
                    true, 1, 2, 1));
            assertEquals(24, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'BREAD'"));
            assertEquals(1, queryInt(statement, "SELECT COUNT(*) FROM caravan_routes"));

            statement.execute("UPDATE caravan_routes SET status = 'ready' WHERE id = 1");
            assertTrue(OperationTransactions.claimCaravan(
                    connection, DatabaseDialect.SQLITE, 1, 1, "EMERALD", 6, 30));
            assertEquals(6, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'EMERALD'"));
            assertFalse(OperationTransactions.claimCaravan(
                    connection, DatabaseDialect.SQLITE, 1, 1, "EMERALD", 6, 30));
            assertEquals(6, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'EMERALD'"));

            statement.execute("INSERT INTO caravan_routes "
                    + "(village_id, destination_id, cargo_item, cargo_amount, return_item, return_amount, "
                    + "status, successful, departed_at_ms, arrives_at_ms) "
                    + "VALUES (1, 'coast', 'BREAD', 1, 'COD', 10, 'ready', 1, 1, 2)");
            assertFalse(OperationTransactions.claimCaravan(
                    connection, DatabaseDialect.SQLITE, 2, 1, "COD", 10, 30));
            assertEquals("ready", queryString(statement,
                    "SELECT status FROM caravan_routes WHERE id = 2"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM warehouse WHERE item_type = 'COD'"));
        }
    }

    @Test
    void crisisContributionAndProsperitySettlementCannotPartiallyApply() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity) VALUES (1, 20)");
            statement.execute("INSERT INTO warehouse(village_id, item_type, amount) VALUES (1, 'POTION', 10)");
            statement.execute("INSERT INTO village_crises "
                    + "(village_id, crisis_id, status, required_item, required_amount, contributed_amount) "
                    + "VALUES (1, 'epidemic', 'active', 'POTION', 8, 0)");

            assertTrue(OperationTransactions.contributeToCrisis(
                    connection, 1, "POTION", 4, 2, .20, 0, 4));
            assertEquals(6, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'POTION'"));
            assertEquals(4, queryInt(statement,
                    "SELECT contributed_amount FROM village_crises WHERE village_id = 1"));

            assertFalse(OperationTransactions.contributeToCrisis(
                    connection, 1, "POTION", 4, 2, .20, 0, 4));
            assertEquals(6, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'POTION'"));
            assertFalse(OperationTransactions.contributeToCrisis(
                    connection, 1, "POTION", 4, 3, .50, 4, 8));
            assertEquals(6, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'POTION'"));
            assertEquals(4, queryInt(statement,
                    "SELECT contributed_amount FROM village_crises WHERE village_id = 1"));

            assertTrue(OperationTransactions.settleCrisis(
                    connection, 1, "epidemic", 999, 10).settled());
            assertEquals(30, queryInt(statement, "SELECT prosperity FROM villages WHERE id = 1"));
            assertEquals(0, queryInt(statement,
                    "SELECT contributed_amount FROM village_crises WHERE village_id = 1"));
            assertFalse(OperationTransactions.settleCrisis(
                    connection, 1, "epidemic", 1000, 10).settled());
            assertEquals(30, queryInt(statement, "SELECT prosperity FROM villages WHERE id = 1"));
        }
    }

    @Test
    void crisisSettlementAppliesAStoredProsperityDeltaOnce() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity) VALUES (1, 20)");
            statement.execute("INSERT INTO village_crises "
                    + "(village_id, crisis_id, status) VALUES (1, 'epidemic', 'active')");

            OperationTransactions.CrisisSettlement reward = OperationTransactions.settleCrisis(
                    connection, 1, "epidemic", 100, 8);
            assertTrue(reward.settled());
            assertEquals(28, reward.prosperity());
            assertEquals(28, queryInt(statement, "SELECT prosperity FROM villages WHERE id = 1"));
            assertFalse(OperationTransactions.settleCrisis(
                    connection, 1, "epidemic", 100, 8).settled());

            statement.execute("UPDATE village_crises SET crisis_id = 'fire', status = 'active' WHERE village_id = 1");
            OperationTransactions.CrisisSettlement penalty = OperationTransactions.settleCrisis(
                    connection, 1, "fire", 200, -40);
            assertTrue(penalty.settled());
            assertEquals(0, penalty.prosperity());
        }
    }

    @Test
    void prosperityAdjustmentsUseStoredValuesAndNeverGoNegative() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity) VALUES (1, 4)");

            OperationTransactions.ProsperityAdjustment increase =
                    OperationTransactions.adjustVillageProsperity(connection, 1, 8);
            assertTrue(increase.adjusted());
            assertEquals(12, increase.prosperity());

            OperationTransactions.ProsperityAdjustment decrease =
                    OperationTransactions.adjustVillageProsperity(connection, 1, -20);
            assertTrue(decrease.adjusted());
            assertEquals(0, decrease.prosperity());
            assertEquals(0, queryInt(statement, "SELECT prosperity FROM villages WHERE id = 1"));
        }
    }

    @Test
    void needSuppliesAndNeedStateCommitOrRollbackTogether() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity) VALUES (1, 0)");
            statement.execute("INSERT INTO warehouse(village_id, item_type, amount) VALUES (1, 'BREAD', 4)");
            statement.execute("INSERT INTO villager_needs "
                    + "(villager_id, hunger, comfort, health, last_updated_ms, last_consumed) "
                    + "VALUES (7, 50, 90, 90, 10, '')");
            OperationTransactions.NeedSupply noSupply = new OperationTransactions.NeedSupply(
                    java.util.List.of(), 0);

            OperationTransactions.NeedsSettlement settled = OperationTransactions.settleVillagerNeeds(
                    connection, 1, 7, 10, 20, 50, 90, 90, "", 65,
                    new OperationTransactions.NeedSupply(java.util.List.of("BREAD"), 25),
                    noSupply, noSupply, java.util.Map.of("BREAD", 0), 0);
            assertTrue(settled.saved());
            assertEquals(75, settled.hunger());
            assertEquals("BREAD", settled.consumed());
            assertEquals(3, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'BREAD'"));
            assertEquals(75, queryInt(statement,
                    "SELECT hunger FROM villager_needs WHERE villager_id = 7"));

            OperationTransactions.NeedsSettlement stale = OperationTransactions.settleVillagerNeeds(
                    connection, 1, 7, 10, 30, 50, 90, 90, "", 65,
                    new OperationTransactions.NeedSupply(java.util.List.of("BREAD"), 25),
                    noSupply, noSupply, java.util.Map.of("BREAD", 0), 0);
            assertFalse(stale.saved());
            assertEquals(3, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'BREAD'"));
            assertEquals(75, queryInt(statement,
                    "SELECT hunger FROM villager_needs WHERE villager_id = 7"));
        }
    }

    @Test
    void orderAcceptanceConsumesCargoAndRecordsPayoutExactlyOnce() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity) VALUES (1, 20)");
            statement.execute("INSERT INTO warehouse(village_id, item_type, amount) VALUES (1, 'WHEAT', 72)");
            statement.execute("INSERT INTO village_orders "
                    + "(id, village_id, status, payout_money) VALUES (7, 1, 'pending', 0)");

            assertTrue(OperationTransactions.acceptOrder(connection, 7, 1,
                    "WHEAT", 32, 4, .10, 50, 8));
            assertEquals(40, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'WHEAT'"));
            assertEquals(28, queryInt(statement, "SELECT prosperity FROM villages WHERE id = 1"));
            assertEquals("payout_pending", queryString(statement,
                    "SELECT status FROM village_orders WHERE id = 7"));
            assertEquals(50, queryInt(statement,
                    "SELECT payout_money FROM village_orders WHERE id = 7"));

            statement.execute("INSERT INTO village_orders "
                    + "(id, village_id, status, payout_money) VALUES (8, 1, 'pending', 0)");
            assertTrue(OperationTransactions.acceptOrder(connection, 8, 1,
                    "WHEAT", 32, 4, .10, 50, 7));
            assertEquals(8, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'WHEAT'"));
            assertEquals(35, queryInt(statement, "SELECT prosperity FROM villages WHERE id = 1"));

            assertFalse(OperationTransactions.acceptOrder(connection, 7, 1,
                    "WHEAT", 4, 0, 0, 10, 40));
            assertEquals(8, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'WHEAT'"));
            assertEquals(35, queryInt(statement, "SELECT prosperity FROM villages WHERE id = 1"));
        }
    }

    @Test
    void visitorAndFestivalRewardsSettleThroughWarehouseExactlyOnce() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE visitor_quests (player_uuid TEXT NOT NULL, "
                    + "visitor_id INTEGER NOT NULL, quest_name TEXT NOT NULL, delivered_amount INTEGER DEFAULT 0, "
                    + "status TEXT NOT NULL, completed_at DATETIME, "
                    + "UNIQUE(player_uuid, visitor_id, quest_name))");
            statement.execute("CREATE TABLE festival_claims (player_uuid TEXT NOT NULL, "
                    + "festival_name TEXT NOT NULL, claimed_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY(player_uuid, festival_name))");
            statement.execute("CREATE TABLE festival_boosts (festival_name TEXT PRIMARY KEY, "
                    + "expires_at INTEGER NOT NULL)");
            statement.execute("INSERT INTO villages(id, prosperity) VALUES (1, 20)");
            statement.execute("INSERT INTO warehouse(village_id, item_type, amount) "
                    + "VALUES (1, 'WHEAT', 12)");
            statement.execute("INSERT INTO visitor_quests(player_uuid, visitor_id, quest_name, status) "
                    + "VALUES ('player', 7, 'harvest', 'accepted')");

            assertTrue(OperationTransactions.completeVisitorQuest(connection,
                    DatabaseDialect.SQLITE, "player", 7, "harvest", 1,
                    "WHEAT", 8, "JACK_O_LANTERN", 1, 2, .10, 50));
            assertEquals(4, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'WHEAT'"));
            assertEquals(1, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'JACK_O_LANTERN'"));
            assertEquals("completed", queryString(statement,
                    "SELECT status FROM visitor_quests WHERE visitor_id = 7"));
            assertFalse(OperationTransactions.completeVisitorQuest(connection,
                    DatabaseDialect.SQLITE, "player", 7, "harvest", 1,
                    "WHEAT", 1, "JACK_O_LANTERN", 1, 0, 0, 50));

            assertTrue(OperationTransactions.claimFestivalReward(connection,
                    DatabaseDialect.SQLITE, "player", "winter#1", 1,
                    "POTION", 3, 50));
            assertFalse(OperationTransactions.claimFestivalReward(connection,
                    DatabaseDialect.SQLITE, "player", "winter#1", 1,
                    "POTION", 3, 50));
            assertEquals(3, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'POTION'"));
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM festival_claims WHERE festival_name = 'winter#1'"));

            OperationTransactions.FestivalClaimResult firstBoost =
                    OperationTransactions.claimFestivalReward(connection,
                            DatabaseDialect.SQLITE, "player-a", "harvest#2", 1,
                            "PUMPKIN", 1, 50, "harvest#2", 5000);
            OperationTransactions.FestivalClaimResult laterClaim =
                    OperationTransactions.claimFestivalReward(connection,
                            DatabaseDialect.SQLITE, "player-b", "harvest#2", 1,
                            "PUMPKIN", 1, 50, "harvest#2", 9000);
            assertTrue(firstBoost.claimed());
            assertTrue(laterClaim.claimed());
            assertEquals(5000, firstBoost.boostExpiry());
            assertEquals(5000, laterClaim.boostExpiry());
            assertEquals(5000, queryInt(statement,
                    "SELECT expires_at FROM festival_boosts WHERE festival_name = 'harvest#2'"));
            assertEquals(2, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'PUMPKIN'"));
            assertFalse(OperationTransactions.claimFestivalReward(connection,
                    DatabaseDialect.SQLITE, "player-a", "harvest#2", 1,
                    "PUMPKIN", 1, 50, "harvest#2", 12000).claimed());

            assertFalse(OperationTransactions.claimFestivalReward(connection,
                    DatabaseDialect.SQLITE, "player", "harvest#1", 1,
                    "PUMPKIN", 64, 10));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM festival_claims WHERE festival_name = 'harvest#1'"));

            int pumpkinsBeforeFailure = queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'PUMPKIN'");
            statement.execute("DROP TABLE festival_boosts");
            assertThrows(java.sql.SQLException.class, () ->
                    OperationTransactions.claimFestivalReward(connection,
                            DatabaseDialect.SQLITE, "player-c", "broken#1", 1,
                            "PUMPKIN", 1, 50, "broken#1", 15000));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM festival_claims WHERE festival_name = 'broken#1'"));
            assertEquals(pumpkinsBeforeFailure, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'PUMPKIN'"));
        }
    }

    @Test
    void marketTradeUpdatesStockQuotaPressureAndHistoryTogether() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity) VALUES (1, 20)");
            statement.execute("INSERT INTO warehouse(village_id, item_type, amount) VALUES (1, 'WHEAT', 20)");

            assertTrue(OperationTransactions.completeMarketTrade(connection,
                    DatabaseDialect.SQLITE, 1, "player", "WHEAT", false,
                    8, 2.0, 4, .10, 50, 1000, 10,
                    0, 0, .25, 2000));
            assertEquals(12, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'WHEAT'"));
            assertEquals(8, queryInt(statement,
                    "SELECT traded_volume FROM market_state WHERE item_type = 'WHEAT'"));
            assertEquals(1, queryInt(statement, "SELECT COUNT(*) FROM market_trades"));

            assertFalse(OperationTransactions.completeMarketTrade(connection,
                    DatabaseDialect.SQLITE, 1, "player", "WHEAT", false,
                    1, 2.0, 4, .10, 50, 1000, 10,
                    0, 0, .30, 2100));
            assertEquals(12, queryInt(statement,
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'WHEAT'"));

            assertFalse(OperationTransactions.completeMarketTrade(connection,
                    DatabaseDialect.SQLITE, 1, "player", "WHEAT", false,
                    3, 2.0, 4, .10, 50, 1000, 10,
                    8, 2000, .35, 2200));
            assertEquals(8, queryInt(statement,
                    "SELECT traded_volume FROM market_state WHERE item_type = 'WHEAT'"));
            assertEquals(1, queryInt(statement, "SELECT COUNT(*) FROM market_trades"));

            assertFalse(OperationTransactions.completeMarketTrade(connection,
                    DatabaseDialect.SQLITE, 1, "player", "CARROT", true,
                    9, 1.5, 0, 0, 20, 3000, 30,
                    0, 0, -.10, 4000));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM warehouse WHERE item_type = 'CARROT'"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM market_state WHERE item_type = 'CARROT'"));
        }
    }

    @Test
    void visitorShopStockAndPerPlayerLimitAreReservedAtomically() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO visitors(id, active) VALUES (7, 1)");

            assertTrue(OperationTransactions.reserveVisitorProduct(connection,
                    DatabaseDialect.SQLITE, 7, "rare_seed", "player-a", 2, 1));
            assertFalse(OperationTransactions.reserveVisitorProduct(connection,
                    DatabaseDialect.SQLITE, 7, "rare_seed", "player-a", 2, 1));
            assertTrue(OperationTransactions.reserveVisitorProduct(connection,
                    DatabaseDialect.SQLITE, 7, "rare_seed", "player-b", 2, 1));
            assertFalse(OperationTransactions.reserveVisitorProduct(connection,
                    DatabaseDialect.SQLITE, 7, "rare_seed", "player-c", 2, 1));
            assertEquals(2, queryInt(statement,
                    "SELECT SUM(purchase_count) FROM visitor_shop_sales WHERE visitor_id = 7"));

            assertTrue(OperationTransactions.releaseVisitorProduct(
                    connection, 7, "rare_seed", "player-a"));
            assertFalse(OperationTransactions.releaseVisitorProduct(
                    connection, 7, "rare_seed", "player-a"));
            assertTrue(OperationTransactions.reserveVisitorProduct(connection,
                    DatabaseDialect.SQLITE, 7, "rare_seed", "player-c", 2, 1));
            assertEquals(2, queryInt(statement,
                    "SELECT SUM(purchase_count) FROM visitor_shop_sales WHERE visitor_id = 7"));

            statement.execute("UPDATE visitors SET active = 0 WHERE id = 7");
            assertFalse(OperationTransactions.reserveVisitorProduct(connection,
                    DatabaseDialect.SQLITE, 7, "other", "player-a", 1, 1));
        }
    }

    @Test
    void growthUpgradesRequireThePaidForVersionAndAvailableSkillPoints() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity, level) VALUES (1, 0, 4)");
            statement.execute("INSERT INTO villagers(id, entity_uuid) VALUES (7, 'villager-7')");

            assertTrue(OperationTransactions.upgradeVillageSkill(connection,
                    DatabaseDialect.SQLITE, 1, "farming", 0, 2));
            assertFalse(OperationTransactions.upgradeVillageSkill(connection,
                    DatabaseDialect.SQLITE, 1, "farming", 0, 2));
            assertTrue(OperationTransactions.upgradeVillageSkill(connection,
                    DatabaseDialect.SQLITE, 1, "farming", 1, 2));
            assertTrue(OperationTransactions.upgradeVillageSkill(connection,
                    DatabaseDialect.SQLITE, 1, "storage", 0, 3));
            assertFalse(OperationTransactions.upgradeVillageSkill(connection,
                    DatabaseDialect.SQLITE, 1, "storage", 1, 3));
            assertEquals(3, queryInt(statement,
                    "SELECT SUM(level) FROM village_upgrades WHERE village_id = 1"));

            assertTrue(OperationTransactions.upgradeVillagerSkill(connection,
                    DatabaseDialect.SQLITE, 7, "harvest", 0, 2));
            assertFalse(OperationTransactions.upgradeVillagerSkill(connection,
                    DatabaseDialect.SQLITE, 7, "harvest", 0, 2));
            assertTrue(OperationTransactions.upgradeVillagerSkill(connection,
                    DatabaseDialect.SQLITE, 7, "harvest", 1, 2));
            assertFalse(OperationTransactions.upgradeVillagerSkill(connection,
                    DatabaseDialect.SQLITE, 7, "harvest", 2, 2));

            assertTrue(OperationTransactions.upgradeSpecialization(connection,
                    DatabaseDialect.SQLITE, 7, "abundance", 0, 3));
            assertFalse(OperationTransactions.upgradeSpecialization(connection,
                    DatabaseDialect.SQLITE, 7, "abundance", 0, 3));
            assertTrue(OperationTransactions.upgradeSpecialization(connection,
                    DatabaseDialect.SQLITE, 7, "abundance", 1, 3));
            assertFalse(OperationTransactions.upgradeSpecialization(connection,
                    DatabaseDialect.SQLITE, 7, "horticulturist", 2, 3));
            assertFalse(OperationTransactions.removeSpecialization(
                    connection, 7, "abundance", 1));
            assertTrue(OperationTransactions.removeSpecialization(
                    connection, 7, "abundance", 2));
        }
    }

    @Test
    void recruitmentReservesPopulationSlotAndPrerequisiteInOneTransaction() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity, level) VALUES (1, 0, 1)");
            statement.execute("INSERT INTO villages(id, prosperity, level) VALUES (2, 0, 1)");
            statement.execute("INSERT INTO villagers(village_id, entity_uuid, profession) "
                    + "VALUES (1, 'farmer-entity', 'farmer')");

            int bakerId = OperationTransactions.recruitVillager(connection,
                    DatabaseDialect.SQLITE, 1, "baker-entity", "baker", 2, "farmer");
            assertTrue(bakerId > 0);
            assertFalse(OperationTransactions.recruitVillager(connection,
                    DatabaseDialect.SQLITE, 1, "third-entity", "fisherman", 2, "") > 0);
            assertFalse(OperationTransactions.recruitVillager(connection,
                    DatabaseDialect.SQLITE, 1, "baker-entity", "fisherman", 3, "") > 0);
            assertFalse(OperationTransactions.recruitVillager(connection,
                    DatabaseDialect.SQLITE, 2, "orphan-baker", "baker", 3, "farmer") > 0);
            assertEquals(2, queryInt(statement,
                    "SELECT COUNT(*) FROM villagers WHERE village_id = 1"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM villagers WHERE village_id = 2"));
        }
    }

    @Test
    void sharedWarehouseStoreOnlyClaimsRemainingCapacity() throws Exception {
        try (Connection connection = database(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO villages(id, prosperity, level) VALUES (1, 0, 1)");

            assertEquals(8, OperationTransactions.storeWarehouseStock(connection,
                    DatabaseDialect.SQLITE, 1, "WHEAT", 8, 10));
            assertEquals(2, OperationTransactions.storeWarehouseStock(connection,
                    DatabaseDialect.SQLITE, 1, "CARROT", 5, 10));
            assertEquals(0, OperationTransactions.storeWarehouseStock(connection,
                    DatabaseDialect.SQLITE, 1, "POTATO", 1, 10));
            assertEquals(10, queryInt(statement,
                    "SELECT SUM(amount) FROM warehouse WHERE village_id = 1"));
        }
    }

    private static Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE villages (id INTEGER PRIMARY KEY, prosperity INTEGER NOT NULL, "
                    + "level INTEGER NOT NULL DEFAULT 1)");
            statement.execute("CREATE TABLE villagers (id INTEGER PRIMARY KEY, "
                    + "village_id INTEGER NOT NULL DEFAULT 0, entity_uuid TEXT NOT NULL UNIQUE, "
                    + "profession TEXT NOT NULL DEFAULT '', level INTEGER NOT NULL DEFAULT 1, "
                    + "experience INTEGER NOT NULL DEFAULT 0, follow_mode TEXT NOT NULL DEFAULT 'FREE')");
            statement.execute("CREATE TABLE village_upgrades (village_id INTEGER NOT NULL, "
                    + "upgrade_id TEXT NOT NULL, level INTEGER NOT NULL, "
                    + "PRIMARY KEY(village_id, upgrade_id))");
            statement.execute("CREATE TABLE villager_upgrades (villager_id INTEGER NOT NULL, "
                    + "skill_id TEXT NOT NULL, level INTEGER NOT NULL, "
                    + "PRIMARY KEY(villager_id, skill_id))");
            statement.execute("CREATE TABLE villager_specializations (villager_id INTEGER PRIMARY KEY, "
                    + "branch_id TEXT NOT NULL, level INTEGER NOT NULL)");
            statement.execute("CREATE TABLE warehouse (village_id INTEGER NOT NULL, item_type TEXT NOT NULL, "
                    + "amount INTEGER NOT NULL, UNIQUE(village_id, item_type))");
            statement.execute("CREATE TABLE caravan_routes (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "village_id INTEGER NOT NULL, destination_id TEXT NOT NULL, cargo_item TEXT NOT NULL, "
                    + "cargo_amount INTEGER NOT NULL, return_item TEXT NOT NULL, return_amount INTEGER NOT NULL, "
                    + "status TEXT NOT NULL, successful BOOLEAN NOT NULL, departed_at_ms INTEGER NOT NULL, "
                    + "arrives_at_ms INTEGER NOT NULL)");
            statement.execute("CREATE TABLE village_crises (village_id INTEGER PRIMARY KEY, "
                    + "crisis_id TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'waiting', "
                    + "required_item TEXT NOT NULL DEFAULT '', required_amount INTEGER NOT NULL DEFAULT 0, "
                    + "contributed_amount INTEGER NOT NULL DEFAULT 0, started_at_ms INTEGER NOT NULL DEFAULT 0, "
                    + "expires_at_ms INTEGER NOT NULL DEFAULT 0, next_roll_at_ms INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE village_orders (id INTEGER PRIMARY KEY, "
                    + "village_id INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending', "
                    + "payout_money REAL NOT NULL DEFAULT 0, completed_at DATETIME)");
            statement.execute("CREATE TABLE market_state (item_type TEXT PRIMARY KEY, "
                    + "pressure REAL NOT NULL DEFAULT 0, traded_volume INTEGER NOT NULL DEFAULT 0, "
                    + "last_updated_ms INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE market_trades (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "village_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, item_type TEXT NOT NULL, "
                    + "direction TEXT NOT NULL, amount INTEGER NOT NULL, unit_price REAL NOT NULL, "
                    + "total_price REAL NOT NULL, traded_at_ms INTEGER NOT NULL)");
            statement.execute("CREATE TABLE visitors (id INTEGER PRIMARY KEY, active BOOLEAN NOT NULL)");
            statement.execute("CREATE TABLE visitor_shop_sales (visitor_id INTEGER NOT NULL, "
                    + "product_id TEXT NOT NULL, player_uuid TEXT NOT NULL, "
                    + "purchase_count INTEGER NOT NULL DEFAULT 0, updated_at DATETIME, "
                    + "PRIMARY KEY(visitor_id, product_id, player_uuid))");
            statement.execute("CREATE TABLE villager_needs (villager_id INTEGER PRIMARY KEY, "
                    + "hunger REAL NOT NULL, comfort REAL NOT NULL, health REAL NOT NULL, "
                    + "last_updated_ms INTEGER NOT NULL, last_consumed TEXT NOT NULL)");
        }
        return connection;
    }

    private static int queryInt(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static String queryString(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
