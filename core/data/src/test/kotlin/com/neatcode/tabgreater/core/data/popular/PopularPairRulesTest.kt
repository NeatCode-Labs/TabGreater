package com.neatcode.tabgreater.core.data.popular

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularPairRulesTest {

    @Test
    fun `major coins survive every rule`() {
        assertKept("bitcoin", "btc", "Bitcoin")
        assertKept("ethereum", "eth", "Ethereum")
        assertKept("binancecoin", "bnb", "BNB")
        assertKept("solana", "sol", "Solana")
        assertKept("ripple", "xrp", "XRP")
        assertKept("dogecoin", "doge", "Dogecoin")
        assertKept("tron", "trx", "TRON")
        assertKept("chainlink", "link", "Chainlink")
    }

    @Test
    fun `stablecoins are excluded by id`() {
        assertExcluded("tether", "usdt", "Tether")
        assertExcluded("usd-coin", "usdc", "USDC")
        assertExcluded("dai", "dai", "Dai")
        assertExcluded("frax", "frax", "Frax")
        assertExcluded("first-digital-usd", "fdusd", "First Digital USD")
        assertExcluded("ethena-usde", "usde", "Ethena USDe")
        assertExcluded("euro-coin", "eurc", "Euro Coin")
    }

    @Test
    fun `a fiat marker in the ticker excludes an unknown stablecoin`() {
        assertExcluded("brand-new-dollar", "nusd", "Brand New Dollar")
        assertExcluded("some-euro-thing", "xeur", "Some Euro Thing")
        assertExcluded("pound-thing", "pgbp", "Pound Thing")
    }

    @Test
    fun `wrapped and staked receipts are excluded`() {
        assertExcluded("wrapped-bitcoin", "wbtc", "Wrapped Bitcoin")
        assertExcluded("wrapped-steth", "wsteth", "Wrapped stETH")
        assertExcluded("staked-ether", "steth", "Lido Staked Ether")
        assertExcluded("weth", "weth", "WETH")
        assertExcluded("wrapped-eeth", "weeth", "Wrapped eETH")
        assertExcluded("coinbase-wrapped-btc", "cbbtc", "Coinbase Wrapped BTC")
        assertExcluded("binance-bridged-usdt-bnb-smart-chain", "busdt", "Binance Bridged USDT")
    }

    @Test
    fun `an unknown wrapper is caught by the name`() {
        assertExcluded("brand-new-thing", "xbtc", "Brand New Wrapped Bitcoin")
        assertExcluded("another-thing", "ysol", "Another Staked Solana")
    }

    @Test
    fun `the w prefix rule only fires on a known base`() {
        assertExcluded("wrapped-bnb", "wbnb", "WBNB")
        // Genuine coins that merely start with a w.
        assertKept("dogwifcoin", "wif", "dogwifhat")
        assertKept("worldcoin-wld", "wld", "Worldcoin")
        assertKept("walrus", "wal", "Walrus")
    }

    @Test
    fun `LEO is kept`() {
        assertKept("leo-token", "leo", "LEO Token")
    }

    @Test
    fun `empty fields are not excluded by accident`() {
        assertFalse(PopularPairRules.isExcluded("", "", ""))
    }

    private fun assertKept(id: String, symbol: String, name: String) {
        assertFalse("$id/$symbol must be kept", PopularPairRules.isExcluded(id, symbol, name))
    }

    private fun assertExcluded(id: String, symbol: String, name: String) {
        assertTrue("$id/$symbol must be excluded", PopularPairRules.isExcluded(id, symbol, name))
    }
}
