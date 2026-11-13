package br.com.cataloghub.service;

import br.com.cataloghub.model.*;

import java.text.Normalizer;
import java.util.*;

/**
 * "Assistente de IA" do hub. Para a demonstração funciona 100% offline com regras/heurísticas:
 *  - classificação de categoria e NCM por palavras-chave;
 *  - geração de título otimizado por canal (respeitando limite de caracteres de cada marketplace);
 *  - geração de descrição;
 *  - precificação por canal considerando comissão, taxa fixa, imposto e margem alvo;
 *  - previsão de ruptura de estoque pela velocidade de vendas;
 *  - insights automáticos no dashboard.
 * Ponto de evolução: substituir os métodos por chamadas a um modelo de linguagem (LLM) via API.
 */
public final class IAService {

    public static final double MARGEM_ALVO = 0.20;
    public static final double IMPOSTO_SIMPLES = 0.06;

    private IAService() { }

    /** palavra-chave -> {categoria, NCM, atributos de destaque} */
    private static final String[][] BASE = {
            {"camiseta", "Moda > Camisetas", "6109.10.00", "100% algodão, toque macio, costura reforçada"},
            {"camisa", "Moda > Camisas", "6205.20.00", "tecido respirável, caimento confortável"},
            {"calca", "Moda > Calças", "6203.42.00", "modelagem moderna, alta durabilidade"},
            {"tenis", "Calçados > Tênis", "6404.11.00", "solado antiderrapante, palmilha com amortecimento"},
            {"fone", "Eletrônicos > Áudio", "8518.30.00", "som nítido, graves potentes, bateria de longa duração"},
            {"caixa de som", "Eletrônicos > Áudio", "8518.22.00", "som potente, conexão Bluetooth, resistente a respingos"},
            {"carregador", "Eletrônicos > Acessórios", "8504.40.10", "carga rápida, proteção contra sobrecarga"},
            {"cabo", "Eletrônicos > Acessórios", "8544.42.00", "alta resistência, transferência rápida"},
            {"capinha", "Celulares > Capas", "3926.90.90", "proteção contra quedas, encaixe perfeito"},
            {"smartwatch", "Eletrônicos > Wearables", "8517.62.77", "monitor cardíaco, notificações, resistente à água"},
            {"garrafa", "Casa > Garrafas Térmicas", "9617.00.10", "mantém a temperatura por horas, livre de BPA"},
            {"caneca", "Casa > Cozinha", "6912.00.00", "cerâmica de alta qualidade, pode ir ao micro-ondas"},
            {"mochila", "Acessórios > Mochilas", "4202.92.00", "compartimento para notebook, tecido impermeável"},
            {"bolsa", "Acessórios > Bolsas", "4202.22.00", "acabamento premium, alça ajustável"},
            {"livro", "Livros", "4901.99.00", "edição revisada, leitura envolvente"},
            {"luminaria", "Casa > Iluminação", "9405.21.00", "luz LED econômica, design moderno"},
            {"perfume", "Beleza > Perfumes", "3303.00.20", "fragrância marcante, alta fixação"},
            {"creme", "Beleza > Cuidados", "3304.99.90", "hidratação intensa, dermatologicamente testado"},
            {"brinquedo", "Brinquedos", "9503.00.99", "seguro para crianças, estimula a criatividade"},
            {"teclado", "Informática > Periféricos", "8471.60.52", "teclas silenciosas, layout ABNT2"},
            {"mouse", "Informática > Periféricos", "8471.60.53", "sensor de alta precisão, design ergonômico"},
            {"panela", "Casa > Cozinha", "7323.93.00", "antiaderente, aquecimento uniforme"},
            {"yoga", "Esporte > Fitness", "9506.91.00", "antiderrapante, fácil de limpar"},
            {"bola", "Esporte > Bolas", "9506.62.00", "alta durabilidade, ótimo controle"},
    };

    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }

    private static String[] classificar(String nome) {
        String n = norm(nome);
        for (String[] r : BASE) if (n.contains(r[0])) return r;
        return null;
    }

    /** Retorna {categoria, ncm}. */
    public static String[] sugerirCategoriaNcm(String nome) {
        String[] r = classificar(nome);
        if (r == null) return new String[]{"Diversos", "9999.99.99"};
        return new String[]{r[1], r[2]};
    }

    public static String gerarDescricao(Produto p) {
        String[] r = classificar(p.getNome());
        String destaques = r != null ? r[3] : "excelente custo-benefício, qualidade garantida";
        StringBuilder sb = new StringBuilder();
        sb.append(p.getNome());
        if (p.getMarca() != null && !p.getMarca().trim().isEmpty()) sb.append(" da marca ").append(p.getMarca());
        sb.append(" é a escolha ideal para quem busca qualidade e praticidade no dia a dia.\n\n");
        sb.append("DESTAQUES:\n");
        for (String d : destaques.split(",")) sb.append("- ").append(capitalizar(d.trim())).append("\n");
        sb.append("\nESPECIFICAÇÕES:\n");
        sb.append("- SKU: ").append(p.getSku()).append("\n");
        if (p.getPesoKg() > 0) sb.append(String.format("- Peso: %.2f kg%n", p.getPesoKg()));
        sb.append("- Produto novo, com nota fiscal e garantia.\n");
        sb.append("\nCompre agora e receba com rapidez e segurança!");
        return sb.toString();
    }

    /** Título otimizado para busca (SEO) de cada marketplace, dentro do limite de caracteres. */
    public static String gerarTitulo(Produto p, Canal c) {
        String base = p.getNome().trim();
        String marca = p.getMarca() == null ? "" : p.getMarca().trim();
        if (!marca.isEmpty() && !norm(base).contains(norm(marca))) base = base + " " + marca;
        String[] r = classificar(p.getNome());
        String atributo = r != null ? capitalizar(r[3].split(",")[0].trim()) : "";
        String titulo;
        switch (c) {
            case MERCADO_LIVRE: titulo = base + " Original"; break;
            case MAGALU: titulo = base + (atributo.isEmpty() ? "" : " - " + atributo); break;
            case SHOPEE: titulo = base + " Envio Rápido" + (atributo.isEmpty() ? "" : " " + atributo); break;
            case TIKTOK_SHOP: titulo = base + " | Oferta"; break;
            default: titulo = base; break;
        }
        return truncarPalavra(titulo, c.getMaxTitulo());
    }

    /** Preço que atinge a margem alvo naquele canal, arredondado para terminar em ,90. */
    public static double sugerirPreco(Produto p, Canal c, double margem) {
        double divisor = 1 - c.getComissao() - IMPOSTO_SIMPLES - margem;
        double preco = (p.getCusto() + c.getTaxaFixa()) / divisor;
        return Math.floor(preco) + 0.90;
    }

    /** Margem líquida real ao vender pelo preço informado no canal. */
    public static double margem(Produto p, Canal c, double preco) {
        if (preco <= 0) return 0;
        double liquido = preco * (1 - c.getComissao() - IMPOSTO_SIMPLES) - c.getTaxaFixa() - p.getCusto();
        return liquido / preco;
    }

    /** Unidades vendidas por dia nos últimos 7 dias (todos os canais). */
    public static double velocidadeVendas(Produto p, List<Pedido> pedidos) {
        long limite = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        int qtd = 0;
        for (Pedido ped : pedidos) {
            if (ped.getStatus() == Pedido.Status.CANCELADO || ped.getData().getTime() < limite) continue;
            for (ItemPedido i : ped.getItens()) if (i.getSku().equalsIgnoreCase(p.getSku())) qtd += i.getQuantidade();
        }
        return qtd / 7.0;
    }

    /** Dias estimados até zerar o estoque (-1 = sem vendas recentes). */
    public static int diasAteRuptura(Produto p, List<Pedido> pedidos) {
        double v = velocidadeVendas(p, pedidos);
        if (v <= 0) return -1;
        return (int) Math.floor(p.getEstoque() / v);
    }

    public static List<String> insights(Repositorio repo) {
        List<String> l = new ArrayList<>();
        // ruptura
        for (Produto p : repo.getProdutos()) {
            if (p.getEstoque() == 0) {
                l.add("[CRÍTICO] " + p.getSku() + " está SEM ESTOQUE - anúncios zerados em todos os canais para evitar overselling.");
                continue;
            }
            int dias = diasAteRuptura(p, repo.getPedidos());
            if (dias >= 0 && dias <= 7) {
                double v = velocidadeVendas(p, repo.getPedidos());
                int sugestao = (int) Math.ceil(v * 30) - p.getEstoque();
                l.add(String.format("[ESTOQUE] %s deve esgotar em ~%d dia(s). Sugestão de compra: %d un. (cobertura de 30 dias).",
                        p.getSku(), dias, Math.max(sugestao, p.getEstoqueMinimo())));
            } else if (p.getEstoque() <= p.getEstoqueMinimo()) {
                l.add("[ESTOQUE] " + p.getSku() + " abaixo do mínimo (" + p.getEstoque() + "/" + p.getEstoqueMinimo() + ").");
            }
        }
        // margem negativa / baixa
        for (Anuncio a : repo.getAnuncios()) {
            Produto p = repo.produto(a.getSku());
            if (p == null) continue;
            double m = margem(p, a.getCanal(), a.getPreco());
            if (m < 0.05) {
                l.add(String.format("[PREÇO] %s em %s com margem de %.1f%%. Preço sugerido: R$ %.2f.",
                        p.getSku(), a.getCanal(), m * 100, sugerirPreco(p, a.getCanal(), MARGEM_ALVO)));
            }
        }
        // melhor canal
        Map<Canal, Double> fat = new EnumMap<>(Canal.class);
        for (Pedido p : repo.getPedidos()) {
            if (p.getStatus() == Pedido.Status.CANCELADO) continue;
            Double v = fat.get(p.getCanal());
            fat.put(p.getCanal(), (v == null ? 0 : v) + p.getTotal());
        }
        Canal melhor = null;
        double max = 0;
        for (Map.Entry<Canal, Double> e : fat.entrySet()) if (e.getValue() > max) { max = e.getValue(); melhor = e.getKey(); }
        if (melhor != null)
            l.add(String.format("[VENDAS] %s é o canal com maior faturamento (R$ %.2f). Considere priorizar campanhas nele.", melhor, max));
        // erros de integração
        int erros = 0;
        for (Anuncio a : repo.getAnuncios()) if (a.getStatus() == Anuncio.Status.ERRO) erros++;
        if (erros > 0) l.add("[INTEGRAÇÃO] " + erros + " anúncio(s) com erro de sincronização. Use 'Reprocessar erros' na aba Canais.");
        // produtos não publicados
        for (Produto p : repo.getProdutos()) {
            int n = repo.anunciosDoProduto(p.getSku()).size();
            if (n < Canal.values().length && p.getEstoque() > 0)
                l.add("[OPORTUNIDADE] " + p.getSku() + " está em " + n + "/" + Canal.values().length + " canais. Publicar nos demais pode aumentar as vendas.");
        }
        if (l.isEmpty()) l.add("Tudo certo! Nenhum alerta no momento.");
        return l;
    }

    private static String capitalizar(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String truncarPalavra(String s, int max) {
        if (s.length() <= max) return s;
        String cut = s.substring(0, max);
        int sp = cut.lastIndexOf(' ');
        return (sp > 10 ? cut.substring(0, sp) : cut).trim();
    }
}
