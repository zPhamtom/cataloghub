package br.com.cataloghub.model;

/**
 * Canais de venda suportados (integrações simuladas para demonstração).
 * Comissões e limites são aproximados e apenas ilustrativos.
 */
public enum Canal {
    MERCADO_LIVRE("Mercado Livre", "MLB", 0.16, 6.00, 60, 0xFFE600),
    MAGALU("Magalu", "MGL", 0.148, 5.00, 150, 0x0086FF),
    SHOPEE("Shopee", "SHP", 0.20, 4.00, 120, 0xEE4D2D),
    TIKTOK_SHOP("TikTok Shop", "TTS", 0.12, 2.00, 100, 0x25F4EE),
    LOJA_PROPRIA("Loja Própria", "LP", 0.0, 0.0, 200, 0x2EA043);

    private final String nome;
    private final String prefixo;
    private final double comissao;
    private final double taxaFixa;
    private final int maxTitulo;
    /** Cor RGB (0xRRGGBB), independente de plataforma (desktop e Android). */
    private final int cor;

    Canal(String nome, String prefixo, double comissao, double taxaFixa, int maxTitulo, int cor) {
        this.nome = nome;
        this.prefixo = prefixo;
        this.comissao = comissao;
        this.taxaFixa = taxaFixa;
        this.maxTitulo = maxTitulo;
        this.cor = cor;
    }

    public String getNome() { return nome; }
    public String getPrefixo() { return prefixo; }
    public double getComissao() { return comissao; }
    public double getTaxaFixa() { return taxaFixa; }
    public int getMaxTitulo() { return maxTitulo; }
    public int getCor() { return cor; }

    @Override
    public String toString() { return nome; }
}
