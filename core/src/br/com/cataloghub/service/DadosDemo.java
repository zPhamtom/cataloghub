package br.com.cataloghub.service;

import br.com.cataloghub.model.*;

import java.util.*;

/** Popula a base com produtos e pedidos de exemplo na primeira execução. */
public final class DadosDemo {

    private DadosDemo() { }

    public static void popular(SyncService sync, PedidoService pedidos) {
        Repositorio repo = Repositorio.get();
        double taxaOriginal = MockMarketplaceConnector.taxaFalha;
        MockMarketplaceConnector.taxaFalha = 0; // carga inicial sem falhas

        Object[][] dados = {
                {"CAM-001", "Camiseta Básica Algodão Preta", "UrbanWear", 49.90, 18.00, 40, 0.20},
                {"TEN-002", "Tênis Esportivo Runner Pro", "Velox", 299.90, 120.00, 12, 0.90},
                {"FON-003", "Fone de Ouvido Bluetooth TWS", "SoundMax", 159.90, 55.00, 25, 0.15},
                {"GAR-004", "Garrafa Térmica Inox 750ml", "EcoLife", 89.90, 30.00, 30, 0.45},
                {"MOC-005", "Mochila Executiva para Notebook 15.6", "TravelPro", 199.90, 75.00, 8, 0.80},
                {"CAN-006", "Caneca Cerâmica Personalizada 350ml", "ArtHome", 39.90, 12.00, 60, 0.35},
                {"SMW-007", "Smartwatch Fit Series 5", "Techno", 349.90, 140.00, 6, 0.10},
                {"CAR-008", "Carregador Turbo USB-C 25W", "VoltX", 79.90, 22.00, 35, 0.12},
                {"LUM-009", "Luminária de Mesa LED Articulada", "Lumina", 129.90, 48.00, 15, 0.70},
                {"YOG-010", "Tapete de Yoga Antiderrapante 6mm", "ZenFit", 109.90, 38.00, 4, 1.10},
        };
        for (Object[] d : dados) {
            String nome = (String) d[1];
            String[] catNcm = IAService.sugerirCategoriaNcm(nome);
            Produto p = new Produto((String) d[0], nome, (String) d[2], catNcm[0], catNcm[1],
                    (Double) d[3], (Double) d[4], (Integer) d[5], (Double) d[6]);
            p.setEan(gerarEan(repo.getProdutos().size()));
            p.setDescricao(IAService.gerarDescricao(p));
            repo.getProdutos().add(p);
        }
        // publica a maioria dos produtos em todos os canais e alguns só em parte (para gerar insights)
        for (Produto p : repo.getProdutos()) {
            List<Canal> canais = new ArrayList<>(Arrays.asList(Canal.values()));
            if (p.getSku().startsWith("LUM") || p.getSku().startsWith("YOG")) canais = Arrays.asList(Canal.MERCADO_LIVRE, Canal.LOJA_PROPRIA);
            sync.publicar(p, canais, true);
        }
        // pedidos históricos (últimos dias)
        Random r = new Random(7);
        for (int i = 0; i < 14; i++) {
            Pedido p = pedidos.simularPedido();
            if (p != null) p.setData(new Date(System.currentTimeMillis() - (long) (r.nextDouble() * 6 * 24 * 3600 * 1000)));
        }
        // alguns já enviados
        int n = 0;
        for (Pedido p : repo.getPedidos()) if (n++ % 2 == 0) pedidos.marcarEnviado(p);

        MockMarketplaceConnector.taxaFalha = taxaOriginal;
        repo.log(Evento.Tipo.SISTEMA, "Dados de demonstração carregados.");
        repo.alterado();
    }

    private static String gerarEan(int seq) {
        String base = "789" + String.format("%09d", 123450000 + seq);
        int soma = 0;
        for (int i = 0; i < 12; i++) soma += (base.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return base + ((10 - soma % 10) % 10);
    }
}
