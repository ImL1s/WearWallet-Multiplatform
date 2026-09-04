package com.cbstudio.wearwallet.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseMigrationTest {

    @Test
    fun testSchemaVersionIsV8() {
        assertEquals(
            expected = 8L,
            actual = CoreWalletDatabase.Schema.version,
            message = "CoreWalletDatabase schema version must be 8 after adding 7.sqm"
        )
    }

    @Test
    fun testMigrationFromV1ToV2ToV3ToV4ToV5PreservesDataAndAppliesDefaults() {
        // 1. Create SQLite in-memory driver
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // 2. Initialize v1 schema individually (without key_alias, key_backend, key_format_version, requires_auth, is_deletion_pending)
        val v1SchemaStatements = listOf(
            """
            CREATE TABLE wallet (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                address TEXT NOT NULL UNIQUE,
                public_key TEXT NOT NULL,
                encrypted_private_key TEXT NOT NULL,
                encrypted_mnemonic TEXT,
                derivation_path TEXT NOT NULL DEFAULT "m/44'/60'/0'/0/0",
                chain_type TEXT NOT NULL DEFAULT 'ETHEREUM',
                wallet_type TEXT NOT NULL DEFAULT 'HOT_WALLET',
                is_active INTEGER NOT NULL DEFAULT 0,
                is_watch_only INTEGER NOT NULL DEFAULT 0,
                master_fingerprint TEXT,
                keystone_sign_request TEXT,
                keystone_sync_data TEXT,
                metadata TEXT DEFAULT '{}',
                avatar_id INTEGER,
                chain_id INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
            )
            """.trimIndent(),
            "CREATE INDEX idx_wallet_address ON wallet(address);",
            "CREATE INDEX idx_wallet_chain_id ON wallet(chain_id);",
            "CREATE INDEX idx_wallet_type ON wallet(wallet_type);",
            "CREATE INDEX idx_wallet_active ON wallet(is_active);",
            """
            CREATE TABLE token (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                wallet_id INTEGER NOT NULL,
                address TEXT NOT NULL,
                symbol TEXT NOT NULL,
                name TEXT NOT NULL,
                decimals INTEGER NOT NULL DEFAULT 18,
                chain_type TEXT NOT NULL,
                chain_id INTEGER NOT NULL,
                balance TEXT NOT NULL DEFAULT '0',
                usd_price REAL,
                price_change_24h REAL,
                logo_url TEXT,
                is_native INTEGER NOT NULL DEFAULT 0,
                is_hidden INTEGER NOT NULL DEFAULT 0,
                contract_type TEXT DEFAULT 'ERC20',
                metadata TEXT DEFAULT '{}',
                last_updated INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                FOREIGN KEY (wallet_id) REFERENCES wallet(id) ON DELETE CASCADE,
                UNIQUE(wallet_id, address, chain_id)
            )
            """.trimIndent(),
            "CREATE INDEX idx_token_wallet_id ON token(wallet_id);",
            "CREATE INDEX idx_token_chain_id ON token(chain_id);",
            "CREATE INDEX idx_token_symbol ON token(symbol);",
            """
            CREATE TABLE IF NOT EXISTS price_alert (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                asset_symbol TEXT NOT NULL,
                asset_name TEXT,
                contract_address TEXT,
                chain_type TEXT NOT NULL DEFAULT 'ETHEREUM',
                chain_id INTEGER NOT NULL DEFAULT 1,
                alert_type TEXT NOT NULL,
                target_price REAL NOT NULL,
                current_price REAL,
                percentage_threshold REAL,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                is_triggered INTEGER NOT NULL DEFAULT 0,
                notification_sent INTEGER NOT NULL DEFAULT 0,
                trigger_count INTEGER NOT NULL DEFAULT 0,
                last_triggered_at INTEGER,
                last_checked_at INTEGER,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                user_notes TEXT,
                webhook_url TEXT,
                repeat_interval INTEGER DEFAULT 0,
                UNIQUE(asset_symbol, chain_type, alert_type, target_price)
            );
            """.trimIndent()
        )

        for (sql in v1SchemaStatements) {
            driver.execute(null, sql, 0)
        }

        // 3. Insert legacy v1 wallet and token records
        val legacyAddress1 = "0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F"
        val legacyAddress2 = "0x1234567890123456789012345678901234567890"

        driver.execute(null, """
            INSERT INTO wallet (
                name, address, public_key, encrypted_private_key, encrypted_mnemonic,
                derivation_path, chain_type, wallet_type, is_active, is_watch_only, chain_id
            ) VALUES (
                'Legacy Ethereum Wallet', '$legacyAddress1', '0x04pubkey1',
                'LEGACY_ENCRYPTED_KEY_PAYLOAD_1', 'LEGACY_ENCRYPTED_MNEMONIC_1',
                "m/44'/60'/0'/0/0", 'ETHEREUM', 'HOT_WALLET', 1, 0, 1
            )
        """.trimIndent(), 0)

        driver.execute(null, """
            INSERT INTO wallet (
                name, address, public_key, encrypted_private_key, encrypted_mnemonic,
                derivation_path, chain_type, wallet_type, is_active, is_watch_only, chain_id
            ) VALUES (
                'Legacy Polygon Wallet', '$legacyAddress2', '0x04pubkey2',
                'LEGACY_ENCRYPTED_KEY_PAYLOAD_2', NULL,
                "m/44'/60'/0'/0/0", 'POLYGON', 'HOT_WALLET', 0, 0, 137
            )
        """.trimIndent(), 0)

        driver.execute(null, """
            INSERT INTO token (
                wallet_id, address, symbol, name, decimals, chain_type, chain_id, balance, is_native
            ) VALUES (
                1, '0x0000000000000000000000000000000000000000', 'ETH', 'Ether', 18, 'ETHEREUM', 1, '1500000000000000000', 1
            )
        """.trimIndent(), 0)

        // 4. Run SQLDelight migration from v1 to v8
        CoreWalletDatabase.Schema.migrate(driver, 1, 8)

        // 5. Instantiate CoreWalletDatabase and verify all records are preserved with default values
        val database = CoreWalletDatabase(driver)
        val wallets = database.walletQueries.selectAll().executeAsList()
        assertEquals(2, wallets.size, "Both legacy wallets must be preserved after migration")

        val wallet1 = wallets.first { it.address == legacyAddress1 }
        assertEquals("Legacy Ethereum Wallet", wallet1.name)
        assertEquals(legacyAddress1, wallet1.address)
        assertEquals("0x04pubkey1", wallet1.public_key)
        assertEquals("LEGACY_ENCRYPTED_KEY_PAYLOAD_1", wallet1.encrypted_private_key)
        assertEquals("LEGACY_ENCRYPTED_MNEMONIC_1", wallet1.encrypted_mnemonic)
        assertEquals("m/44'/60'/0'/0/0", wallet1.derivation_path)
        assertEquals("ETHEREUM", wallet1.chain_type)
        assertEquals("HOT_WALLET", wallet1.wallet_type)
        assertEquals(1L, wallet1.is_active)
        assertEquals(0L, wallet1.is_watch_only)
        assertEquals(1L, wallet1.chain_id)

        // Verify newly added columns receive their default migration values
        assertNull(wallet1.key_alias, "key_alias must be null on migrated legacy record")
        assertEquals("LEGACY_ENCRYPTED_PREFS", wallet1.key_backend, "key_backend must default to LEGACY_ENCRYPTED_PREFS")
        assertEquals(1L, wallet1.key_format_version, "key_format_version must default to 1")
        assertEquals(0L, wallet1.requires_auth, "requires_auth must default to 0 for legacy records")
        assertEquals(0L, wallet1.is_deletion_pending, "is_deletion_pending must default to 0")

        val wallet2 = wallets.first { it.address == legacyAddress2 }
        assertEquals("Legacy Polygon Wallet", wallet2.name)
        assertEquals("LEGACY_ENCRYPTED_PREFS", wallet2.key_backend)
        assertEquals(1L, wallet2.key_format_version)
        assertEquals(0L, wallet2.requires_auth)
        assertEquals(0L, wallet2.is_deletion_pending)
        assertNull(wallet2.key_alias)

        // 6. Verify querying by key_alias index works
        val nullAliasWallets = database.walletQueries.selectByKeyAlias(null).executeAsList()
        assertEquals(2, nullAliasWallets.size, "Both un-provisioned legacy wallets should match key_alias = null")

        // 7. Verify token table relations remain intact
        val tokens = database.tokenQueries.selectByWalletId(wallet1.id).executeAsList()
        assertEquals(1, tokens.size)
        assertEquals("ETH", tokens.first().symbol)

        // 8. Verify updating new columns on migrated record (e.g. during modern provisioning migration)
        database.walletQueries.updateEncryptedSecrets(
            encrypted_private_key = "WWEN_MIGRATED_ENVELOPE_DATA",
            encrypted_mnemonic = null,
            key_alias = "ww_key_migrated_eth_001",
            key_backend = "KEYSTORE",
            key_format_version = 2L,
            requires_auth = 1L,
            id = wallet1.id
        )

        val updatedWallet1 = database.walletQueries.selectById(wallet1.id).executeAsOne()
        assertEquals("ww_key_migrated_eth_001", updatedWallet1.key_alias)
        assertEquals("KEYSTORE", updatedWallet1.key_backend)
        assertEquals(2L, updatedWallet1.key_format_version)
        assertEquals(1L, updatedWallet1.requires_auth)
        assertEquals("WWEN_MIGRATED_ENVELOPE_DATA", updatedWallet1.encrypted_private_key)

        // 9. Verify querying by newly assigned key_alias via the index
        val queriedByAlias = database.walletQueries.selectByKeyAlias("ww_key_migrated_eth_001").executeAsOne()
        assertEquals(wallet1.id, queriedByAlias.id)
        assertEquals("Legacy Ethereum Wallet", queriedByAlias.name)

        // 10. Verify 2-phase deletion tombstone queries
        assertEquals(2, database.walletQueries.selectAllActiveWallets().executeAsList().size)
        assertEquals(0, database.walletQueries.selectDeletionPending().executeAsList().size)

        database.walletQueries.markDeletionPending(wallet1.id)
        assertEquals(1, database.walletQueries.selectAllActiveWallets().executeAsList().size)
        val pendingList = database.walletQueries.selectDeletionPending().executeAsList()
        assertEquals(1, pendingList.size)
        assertEquals(wallet1.id, pendingList.first().id)
        assertEquals(1L, pendingList.first().is_deletion_pending)

        // 11. Verify StagingJournal table exists and operations succeed
        database.stagingJournalQueries.insertJournal(
            session_id = "sess_migration_test_001",
            staged_alias = "ww_key_migrated_eth_001",
            backup_id = "ww_backup_migrated_eth_001",
            state = "KEY_STAGED",
            created_at = 1000L,
            expires_at = 61000L
        )
        val pendingJournals = database.stagingJournalQueries.selectPendingJournals().executeAsList()
        assertEquals(1, pendingJournals.size)
        assertEquals("sess_migration_test_001", pendingJournals.first().session_id)
        assertEquals("KEY_STAGED", pendingJournals.first().state)

        database.stagingJournalQueries.updateJournalState("COMMITTED", "sess_migration_test_001")
        assertEquals(0, database.stagingJournalQueries.selectPendingJournals().executeAsList().size)

        // 12. Verify DeletionJournal table exists and operations succeed (Schema v5)
        database.deletionJournalQueries.insertDeletionJournal(
            wallet_id = wallet1.id,
            key_alias = "ww_key_migrated_eth_001",
            state = "DELETE_AUTHORIZED",
            last_error = null,
            retry_count = 0L,
            created_at = 1000L,
            updated_at = 1000L
        )
        val pendingDeletions = database.deletionJournalQueries.selectPendingDeletions().executeAsList()
        assertEquals(1, pendingDeletions.size)
        assertEquals(wallet1.id, pendingDeletions.first().wallet_id)
        assertEquals("DELETE_AUTHORIZED", pendingDeletions.first().state)

        database.deletionJournalQueries.updateDeletionStateCas(
            newState = "TOMBSTONED",
            lastError = null,
            updatedAt = 2000L,
            walletId = wallet1.id,
            expectedState = "DELETE_AUTHORIZED"
        )
        val tombstoned = database.deletionJournalQueries.selectByWalletId(wallet1.id).executeAsOne()
        assertEquals("TOMBSTONED", tombstoned.state)

        database.deletionJournalQueries.updateDeletionStateCas(
            newState = "COMPLETED",
            lastError = null,
            updatedAt = 3000L,
            walletId = wallet1.id,
            expectedState = "TOMBSTONED"
        )
        assertEquals(0, database.deletionJournalQueries.selectPendingDeletions().executeAsList().size)

        // 13. Verify PriceAlert table exists and per-wallet isolation works (Schema v7)
        database.priceAlertQueries.insert(
            wallet_id = wallet1.id.toString(),
            asset_symbol = "ETH",
            asset_name = "Ether",
            contract_address = null,
            chain_type = "ETHEREUM",
            chain_id = 1L,
            alert_type = "ABOVE",
            target_price = 3000.0,
            current_price = 2500.0,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Sell high",
            webhook_url = null,
            repeat_interval = 0L
        )
        database.priceAlertQueries.insert(
            wallet_id = wallet2.id.toString(),
            asset_symbol = "MATIC",
            asset_name = "Polygon",
            contract_address = null,
            chain_type = "POLYGON",
            chain_id = 137L,
            alert_type = "BELOW",
            target_price = 0.5,
            current_price = 0.8,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Buy low",
            webhook_url = null,
            repeat_interval = 0L
        )
        assertEquals(1, database.priceAlertQueries.selectByWalletId(wallet1.id.toString()).executeAsList().size)
        assertEquals(1, database.priceAlertQueries.selectByWalletId(wallet2.id.toString()).executeAsList().size)

        database.priceAlertQueries.deleteByWalletId(wallet1.id.toString())
        assertEquals(0L, database.priceAlertQueries.countByWalletId(wallet1.id.toString()).executeAsOne())
        assertEquals(1L, database.priceAlertQueries.countByWalletId(wallet2.id.toString()).executeAsOne())

        // 14. Verify Schema v8 Table Rebuild: Per-wallet identical alert insertion without UNIQUE collision
        database.priceAlertQueries.insert(
            wallet_id = wallet1.id.toString(),
            asset_symbol = "BTC",
            asset_name = "Bitcoin",
            contract_address = null,
            chain_type = "BITCOIN",
            chain_id = 0L,
            alert_type = "ABOVE",
            target_price = 100000.0,
            current_price = 95000.0,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Wallet 1 BTC alert",
            webhook_url = null,
            repeat_interval = 0L
        )
        database.priceAlertQueries.insert(
            wallet_id = wallet2.id.toString(),
            asset_symbol = "BTC",
            asset_name = "Bitcoin",
            contract_address = null,
            chain_type = "BITCOIN",
            chain_id = 0L,
            alert_type = "ABOVE",
            target_price = 100000.0,
            current_price = 95000.0,
            percentage_threshold = null,
            is_enabled = 1L,
            user_notes = "Wallet 2 BTC alert",
            webhook_url = null,
            repeat_interval = 0L
        )
        assertTrue(
            database.priceAlertQueries.existsBySameConfig(
                wallet_id = wallet1.id.toString(),
                asset_symbol = "BTC",
                chain_type = "BITCOIN",
                alert_type = "ABOVE",
                target_price = 100000.0
            ).executeAsOne(),
            "existsBySameConfig must return true for wallet 1"
        )
        assertTrue(
            database.priceAlertQueries.existsBySameConfig(
                wallet_id = wallet2.id.toString(),
                asset_symbol = "BTC",
                chain_type = "BITCOIN",
                alert_type = "ABOVE",
                target_price = 100000.0
            ).executeAsOne(),
            "existsBySameConfig must return true for wallet 2"
        )
    }

    @Test
    fun testFreshDatabaseCreationMatchesV5Schema() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(driver)

        val database = CoreWalletDatabase(driver)
        database.walletQueries.insert(
            name = "Fresh Production Wallet",
            address = "0xFreshEthAddress789",
            public_key = "0x04freshpubkey",
            encrypted_private_key = "WWEN_V2_PAYLOAD",
            encrypted_mnemonic = null,
            derivation_path = "m/44'/60'/0'/0/0",
            chain_type = "ETHEREUM",
            wallet_type = "HOT_WALLET",
            is_watch_only = 0L,
            master_fingerprint = null,
            keystone_sign_request = null,
            keystone_sync_data = null,
            metadata = "{}",
            avatar_id = null,
            chain_id = 1L,
            key_alias = "ww_key_fresh_uuid_123",
            key_backend = "KEYSTORE",
            key_format_version = 2L,
            requires_auth = 1L,
            is_deletion_pending = 0L
        )

        val inserted = database.walletQueries.selectByKeyAlias("ww_key_fresh_uuid_123").executeAsOne()
        assertEquals("Fresh Production Wallet", inserted.name)
        assertEquals("0xFreshEthAddress789", inserted.address)
        assertEquals("ww_key_fresh_uuid_123", inserted.key_alias)
        assertEquals("KEYSTORE", inserted.key_backend)
        assertEquals(2L, inserted.key_format_version)
        assertEquals(1L, inserted.requires_auth)
        assertEquals(0L, inserted.is_deletion_pending)

        // Verify active wallets query
        val activeWallets = database.walletQueries.selectAllActiveWallets().executeAsList()
        assertEquals(1, activeWallets.size)

        // Mark deletion pending
        database.walletQueries.markDeletionPending(inserted.id)
        val afterMark = database.walletQueries.selectAllActiveWallets().executeAsList()
        assertEquals(0, afterMark.size)
        val pending = database.walletQueries.selectDeletionPending().executeAsList()
        assertEquals(1, pending.size)
        assertEquals(inserted.id, pending.first().id)

        // Test staging journal on fresh DB
        database.stagingJournalQueries.insertJournal(
            session_id = "sess_fresh_001",
            staged_alias = "ww_key_fresh_uuid_123",
            backup_id = "ww_backup_fresh_001",
            state = "PREPARED",
            created_at = 2000L,
            expires_at = 62000L
        )
        val journals = database.stagingJournalQueries.selectAllJournals().executeAsList()
        assertEquals(1, journals.size)
        assertEquals("PREPARED", journals.first().state)

        // Test deletion journal on fresh DB
        database.deletionJournalQueries.insertDeletionJournal(
            wallet_id = inserted.id,
            key_alias = "ww_key_fresh_uuid_123",
            state = "DELETE_AUTHORIZED",
            last_error = null,
            retry_count = 0L,
            created_at = 2000L,
            updated_at = 2000L
        )
        val deletions = database.deletionJournalQueries.selectAllDeletionJournals().executeAsList()
        assertEquals(1, deletions.size)
        assertEquals("DELETE_AUTHORIZED", deletions.first().state)
    }

    @Test
    fun testMultiWalletMigrationAndIndexQueries() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // 1. Create v1 schema with 0.sqm baseline
        val v1SchemaStatements = listOf(
            """
            CREATE TABLE wallet (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                address TEXT NOT NULL UNIQUE,
                public_key TEXT NOT NULL,
                encrypted_private_key TEXT NOT NULL,
                encrypted_mnemonic TEXT,
                derivation_path TEXT NOT NULL DEFAULT "m/44'/60'/0'/0/0",
                chain_type TEXT NOT NULL DEFAULT 'ETHEREUM',
                wallet_type TEXT NOT NULL DEFAULT 'HOT_WALLET',
                is_active INTEGER NOT NULL DEFAULT 0,
                is_watch_only INTEGER NOT NULL DEFAULT 0,
                master_fingerprint TEXT,
                keystone_sign_request TEXT,
                keystone_sync_data TEXT,
                metadata TEXT DEFAULT '{}',
                avatar_id INTEGER,
                chain_id INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
            )
            """.trimIndent(),
            "CREATE INDEX idx_wallet_address ON wallet(address);",
            "CREATE INDEX idx_wallet_chain_id ON wallet(chain_id);",
            "CREATE INDEX idx_wallet_type ON wallet(wallet_type);",
            "CREATE INDEX idx_wallet_active ON wallet(is_active);",
            """
            CREATE TABLE IF NOT EXISTS price_alert (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                asset_symbol TEXT NOT NULL,
                asset_name TEXT,
                contract_address TEXT,
                chain_type TEXT NOT NULL DEFAULT 'ETHEREUM',
                chain_id INTEGER NOT NULL DEFAULT 1,
                alert_type TEXT NOT NULL,
                target_price REAL NOT NULL,
                current_price REAL,
                percentage_threshold REAL,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                is_triggered INTEGER NOT NULL DEFAULT 0,
                notification_sent INTEGER NOT NULL DEFAULT 0,
                trigger_count INTEGER NOT NULL DEFAULT 0,
                last_triggered_at INTEGER,
                last_checked_at INTEGER,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                user_notes TEXT,
                webhook_url TEXT,
                repeat_interval INTEGER DEFAULT 0,
                UNIQUE(asset_symbol, chain_type, alert_type, target_price)
            );
            """.trimIndent()
        )
        v1SchemaStatements.forEach { driver.execute(null, it, 0) }

        // 2. Insert 5 diverse legacy wallets
        for (i in 1..5) {
            val addr = "0x" + i.toString().padStart(40, '0')
            driver.execute(null, """
                INSERT INTO wallet (
                    name, address, public_key, encrypted_private_key, encrypted_mnemonic,
                    chain_type, wallet_type, is_active, is_watch_only, chain_id
                ) VALUES (
                    'Wallet #$i', '$addr', '0xpub$i',
                    'LEGACY_CIPHERTEXT_$i', 'LEGACY_MNEMONIC_$i',
                    'ETHEREUM', 'HOT_WALLET', ${if (i == 1) 1 else 0}, 0, 1
                )
            """.trimIndent(), 0)
        }

        // 3. Migrate v1 -> v7 step-by-step
        CoreWalletDatabase.Schema.migrate(driver, 1, 2)
        CoreWalletDatabase.Schema.migrate(driver, 2, 3)
        CoreWalletDatabase.Schema.migrate(driver, 3, 4)
        CoreWalletDatabase.Schema.migrate(driver, 4, 5)
        CoreWalletDatabase.Schema.migrate(driver, 5, 6)
        CoreWalletDatabase.Schema.migrate(driver, 6, 7)
        CoreWalletDatabase.Schema.migrate(driver, 7, 8)
        val db = CoreWalletDatabase(driver)

        // 4. Verify all 5 exist and have default v3 columns
        val allWallets = db.walletQueries.selectAll().executeAsList()
        assertEquals(5, allWallets.size)

        for (w in allWallets) {
            assertNull(w.key_alias)
            assertEquals("LEGACY_ENCRYPTED_PREFS", w.key_backend)
            assertEquals(1L, w.key_format_version)
            assertEquals(0L, w.requires_auth)
            assertEquals(0L, w.is_deletion_pending)
        }

        // 5. Migrate individual keys and verify indexing
        for (w in allWallets) {
            val assignedAlias = "ww_alias_generated_${w.id}"
            db.walletQueries.updateEncryptedSecrets(
                encrypted_private_key = "WWEN_PAYLOAD_${w.id}",
                encrypted_mnemonic = null,
                key_alias = assignedAlias,
                key_backend = "KEYSTORE",
                key_format_version = 2L,
                requires_auth = 1L,
                id = w.id
            )

            val fetched = db.walletQueries.selectByKeyAlias(assignedAlias).executeAsOne()
            assertEquals(w.id, fetched.id)
            assertEquals("KEYSTORE", fetched.key_backend)
            assertEquals(2L, fetched.key_format_version)
            assertEquals(1L, fetched.requires_auth)
        }

        // 6. Verify selectByKeyAlias returns empty for non-existent alias
        val nonExistent = db.walletQueries.selectByKeyAlias("non_existent_key_alias_xyz").executeAsList()
        assertTrue(nonExistent.isEmpty())
    }
}
