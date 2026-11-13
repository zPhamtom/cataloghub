package br.com.cataloghub.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import br.com.cataloghub.model.*;
import br.com.cataloghub.service.*;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Versão Android do CatalogHub. Usa exatamente as mesmas regras de negócio da versão desktop
 * (pasta core/: catálogo central, sincronização, pedidos, NF-e simulada e IA).
 * A interface é montada por código, sem bibliotecas externas.
 */
public class MainActivity extends Activity {

    // ---------- cores ----------
    private static final int VERDE_APP = 0xFF00966E, VERDE_ESCURO = 0xFF00694F, FUNDO = 0xFFF2F4F7,
            TEXTO = 0xFF263238, SUAVE = 0xFF6B7780, VERDE = 0xFF1E8C3C, VERMELHO = 0xFFC62828,
            LARANJA = 0xFFDC7800, AZUL = 0xFF1E64C8, CINZA = 0xFF888888, BORDA = 0xFFDDE1E5;

    private static final String[] ABAS = {"Painel", "Catálogo", "Canais", "Pedidos", "Notas fiscais", "Log"};
    private static final int MAX_ITENS_LISTA = 150;

    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private final SimpleDateFormat dataHora = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault());

    private Repositorio repo;
    private SyncService sync;
    private PedidoService pedidos;

    private LinearLayout conteudo;
    private ScrollView rolagem;
    private final TextView[] botoesAba = new TextView[ABAS.length];
    private int abaAtual = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean simulacaoAuto = false;
    private final Runnable tickSimulacao = new Runnable() {
        @Override public void run() {
            if (!simulacaoAuto) return;
            pedidos.simularPedido();
            repo.alterado();
            handler.postDelayed(this, 3000);
        }
    };
    private final Runnable ouvinte = this::renderizar;

    private String busca = "";
    private Canal filtroAnuncios = null, filtroPedidos = null;

    // =====================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(VERDE_ESCURO);

        Repositorio.setPastaDados(getFilesDir());
        repo = Repositorio.get();
        sync = new SyncService();
        pedidos = new PedidoService(sync, new FiscalService());
        if (repo.getProdutos().isEmpty()) DadosDemo.popular(sync, pedidos);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(FUNDO);

        // cabeçalho
        LinearLayout cab = new LinearLayout(this);
        cab.setOrientation(LinearLayout.VERTICAL);
        cab.setBackgroundColor(VERDE_APP);
        cab.setPadding(dp(16), dp(14), dp(16), dp(10));
        TextView titulo = texto("CatalogHub", 22, Color.WHITE, true);
        TextView sub = texto("Catálogo único • Estoque sincronizado • NF-e automática • IA", 12, 0xFFDCF5EB, false);
        cab.addView(titulo);
        cab.addView(sub);
        raiz.addView(cab);

        // barra de abas
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(VERDE_APP);
        LinearLayout barra = new LinearLayout(this);
        barra.setPadding(dp(8), 0, dp(8), dp(8));
        for (int i = 0; i < ABAS.length; i++) {
            final int idx = i;
            TextView b = texto(ABAS[i], 14, Color.WHITE, true);
            b.setPadding(dp(14), dp(8), dp(14), dp(8));
            b.setOnClickListener(v -> { abaAtual = idx; renderizar(); rolagem.scrollTo(0, 0); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(6);
            barra.addView(b, lp);
            botoesAba[i] = b;
        }
        hs.addView(barra);
        raiz.addView(hs);

        rolagem = new ScrollView(this);
        conteudo = new LinearLayout(this);
        conteudo.setOrientation(LinearLayout.VERTICAL);
        conteudo.setPadding(dp(12), dp(12), dp(12), dp(24));
        rolagem.addView(conteudo);
        raiz.addView(rolagem, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(raiz);
        repo.addListener(ouvinte);
        renderizar();
    }

    @Override
    protected void onDestroy() {
        simulacaoAuto = false;
        handler.removeCallbacksAndMessages(null);
        repo.removeListener(ouvinte);
        super.onDestroy();
    }

    private void renderizar() {
        for (int i = 0; i < botoesAba.length; i++) {
            boolean sel = i == abaAtual;
            botoesAba[i].setTextColor(sel ? VERDE_ESCURO : Color.WHITE);
            botoesAba[i].setBackground(sel ? fundo(Color.WHITE, 18, 0) : fundo(0x33FFFFFF, 18, 0));
        }
        conteudo.removeAllViews();
        switch (abaAtual) {
            case 0: telaPainel(); break;
            case 1: telaCatalogo(); break;
            case 2: telaCanais(); break;
            case 3: telaPedidos(); break;
            case 4: telaNotas(); break;
            default: telaLog(); break;
        }
    }

    // =====================================================================
    // PAINEL
    // =====================================================================
    private void telaPainel() {
        int estoque = 0, semEstoque = 0;
        for (Produto p : repo.getProdutos()) { estoque += p.getEstoque(); if (p.getEstoque() == 0) semEstoque++; }
        int validos = 0, aguardando = 0;
        double fat = 0;
        Map<Canal, double[]> porCanal = new EnumMap<>(Canal.class);
        for (Canal c : Canal.values()) porCanal.put(c, new double[2]);
        for (Pedido p : repo.getPedidos()) {
            if (p.getStatus() == Pedido.Status.CANCELADO) continue;
            validos++;
            if (p.getStatus() == Pedido.Status.RECEBIDO) aguardando++;
            fat += p.getTotal();
            porCanal.get(p.getCanal())[0]++;
            porCanal.get(p.getCanal())[1] += p.getTotal();
        }
        int nfs = 0;
        for (NotaFiscal n : repo.getNotas()) if (n.getStatus() == NotaFiscal.Status.AUTORIZADA) nfs++;

        linhaCards(cardKpi("Produtos", String.valueOf(repo.getProdutos().size()), repo.getAnuncios().size() + " anúncios"),
                cardKpi("Estoque central", estoque + " un.", semEstoque > 0 ? semEstoque + " sem estoque" : "sincronizado"));
        linhaCards(cardKpi("Pedidos", String.valueOf(validos), aguardando + " a faturar"),
                cardKpi("NF-e emitidas", String.valueOf(nfs), repo.isFaturamentoAutomatico() ? "emissão automática" : "emissão manual"));
        linhaCards(cardKpi("Faturamento total", moeda.format(fat), "todos os canais"), null);

        botoes(botaoPrimario("Sincronizar canais", () -> {
            int[] r = sync.sincronizarTudo();
            repo.alterado();
            toast(r[0] + " sincronizado(s), " + r[1] + " com erro/pendente");
        }), botao("Simular pedido", this::simularUm));
        botoes(botao(simulacaoAuto ? "■ Parar simulação automática" : "▶ Simulação automática (1 pedido/3s)", this::alternarSimulacao));

        secao("Faturamento por canal");
        LinearLayout card = card();
        double max = 1;
        for (double[] d : porCanal.values()) max = Math.max(max, d[1]);
        for (Canal c : Canal.values()) {
            double[] d = porCanal.get(c);
            LinearLayout linha = new LinearLayout(this);
            linha.setGravity(Gravity.CENTER_VERTICAL);
            linha.setPadding(0, dp(4), 0, dp(4));
            TextView nome = texto(c.getNome(), 13, TEXTO, false);
            linha.addView(nome, new LinearLayout.LayoutParams(dp(96), -2));
            LinearLayout barra = new LinearLayout(this);
            View cheio = new View(this);
            cheio.setBackground(fundo(0xFF000000 | c.getCor(), 4, 0));
            float frac = (float) (d[1] / max);
            barra.addView(cheio, new LinearLayout.LayoutParams(0, dp(18), Math.max(frac, 0.01f)));
            barra.addView(new View(this), new LinearLayout.LayoutParams(0, dp(18), Math.max(1 - frac, 0.0001f)));
            linha.addView(barra, new LinearLayout.LayoutParams(0, -2, 1));
            TextView v = texto(moeda.format(d[1]) + "\n" + (int) d[0] + " ped.", 11, SUAVE, false);
            v.setGravity(Gravity.END);
            linha.addView(v, new LinearLayout.LayoutParams(dp(84), -2));
            card.addView(linha);
        }
        conteudo.addView(card);

        secao("Assistente IA — insights");
        for (String s : IAService.insights(repo)) {
            LinearLayout c = card();
            int cor = s.startsWith("[CRÍTICO]") ? VERMELHO : s.startsWith("[ESTOQUE]") || s.startsWith("[INTEGRAÇÃO]") ? LARANJA
                    : s.startsWith("[PREÇO]") ? AZUL : s.startsWith("[VENDAS]") ? VERDE : TEXTO;
            c.addView(texto(s, 13, cor, false));
            conteudo.addView(c);
        }

        secao("Demonstração");
        botoes(botao("Resetar dados de exemplo", () -> confirmar("Apagar tudo e recarregar os dados de exemplo?", () -> {
            repo.resetar();
            DadosDemo.popular(sync, pedidos);
        })));
    }

    private void simularUm() {
        Pedido p = pedidos.simularPedido();
        repo.alterado();
        if (p == null) toast("Nenhum anúncio com estoque para gerar pedido");
        else toast("Pedido #" + p.getNumero() + " recebido de " + p.getCanal() + " — " + moeda.format(p.getTotal()));
    }

    private void alternarSimulacao() {
        simulacaoAuto = !simulacaoAuto;
        handler.removeCallbacks(tickSimulacao);
        if (simulacaoAuto) handler.postDelayed(tickSimulacao, 500);
        toast(simulacaoAuto ? "Simulação automática ligada" : "Simulação automática desligada");
        renderizar();
    }

    // =====================================================================
    // CATÁLOGO
    // =====================================================================
    private void telaCatalogo() {
        botoes(botaoPrimario("+ Novo produto", () -> formularioProduto(null)),
                botao("Importar planilha exemplo", this::importarExemplo));

        EditText campoBusca = new EditText(this);
        campoBusca.setHint("Buscar por SKU, nome, marca ou categoria");
        campoBusca.setSingleLine(true);
        campoBusca.setText(busca);
        campoBusca.setTextSize(14);
        campoBusca.setBackground(fundo(Color.WHITE, 10, BORDA));
        campoBusca.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        conteudo.addView(campoBusca, lp);

        final LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        conteudo.addView(lista);
        campoBusca.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { busca = e.toString(); preencherCatalogo(lista); }
        });
        preencherCatalogo(lista);
    }

    private void preencherCatalogo(LinearLayout lista) {
        lista.removeAllViews();
        String f = busca.trim().toLowerCase(Locale.ROOT);
        for (final Produto p : repo.getProdutos()) {
            if (!f.isEmpty() && !(p.getSku() + " " + p.getNome() + " " + p.getMarca() + " " + p.getCategoria())
                    .toLowerCase(Locale.ROOT).contains(f)) continue;
            LinearLayout c = card();
            c.addView(texto(p.getNome(), 15, TEXTO, true));
            c.addView(texto(p.getSku() + " • " + p.getMarca() + " • " + p.getCategoria(), 12, SUAVE, false));
            LinearLayout l = new LinearLayout(this);
            l.setPadding(0, dp(6), 0, 0);
            l.addView(texto(moeda.format(p.getPreco()), 15, VERDE_ESCURO, true), new LinearLayout.LayoutParams(0, -2, 1));
            String sit = p.getEstoque() == 0 ? "SEM ESTOQUE" : p.getEstoque() <= p.getEstoqueMinimo() ? "BAIXO" : "OK";
            l.addView(etiqueta("Estoque " + p.getEstoque(), corStatus(sit)));
            l.addView(espaco(6));
            l.addView(etiqueta(repo.anunciosDoProduto(p.getSku()).size() + "/" + Canal.values().length + " canais", AZUL));
            c.addView(l);
            c.setOnClickListener(v -> detalheProduto(p));
            lista.addView(c);
        }
        if (lista.getChildCount() == 0) lista.addView(texto("Nenhum produto encontrado.", 14, SUAVE, false));
    }

    private void detalheProduto(final Produto p) {
        LinearLayout v = vertical();
        int dias = IAService.diasAteRuptura(p, repo.getPedidos());
        v.addView(texto(p.getSku() + " • NCM " + p.getNcm(), 12, SUAVE, false));
        v.addView(texto("Preço base " + moeda.format(p.getPreco()) + " • Custo " + moeda.format(p.getCusto())
                + "\nEstoque central: " + p.getEstoque() + " un.", 14, TEXTO, false));
        v.addView(texto("Previsão IA: " + (dias < 0 ? "sem vendas nos últimos 7 dias" : "estoque esgota em ~" + dias + " dia(s)"),
                13, dias >= 0 && dias <= 7 ? LARANJA : VERDE, true));
        v.addView(espacoV(8));
        v.addView(texto("Anúncios por canal", 14, VERDE_ESCURO, true));
        for (Canal c : Canal.values()) {
            Anuncio a = repo.anuncio(p.getSku(), c);
            double sug = IAService.sugerirPreco(p, c, IAService.MARGEM_ALVO);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(10), dp(6), dp(6), dp(6));
            box.setBackground(faixaLateral(c.getCor()));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            if (a == null) {
                box.addView(texto(c.getNome() + " — não publicado", 13, SUAVE, true));
                box.addView(texto("Preço sugerido IA: " + moeda.format(sug), 12, AZUL, false));
            } else {
                box.addView(texto(c.getNome() + " — " + a.getStatus(), 13, corStatus(a.getStatus().name()), true));
                box.addView(texto(a.getTitulo(), 12, TEXTO, false));
                box.addView(texto("Preço " + moeda.format(a.getPreco()) + " • margem "
                        + String.format(Locale.ROOT, "%.1f%%", IAService.margem(p, c, a.getPreco()) * 100)
                        + " • sugerido IA " + moeda.format(sug), 12, SUAVE, false));
            }
            v.addView(box, lp);
        }
        final AlertDialog[] ref = new AlertDialog[1];
        v.addView(espacoV(10));
        LinearLayout acoes = new LinearLayout(this);
        acoes.addView(botao("Editar", () -> { ref[0].dismiss(); formularioProduto(p); }), new LinearLayout.LayoutParams(0, -2, 1));
        acoes.addView(espaco(6));
        acoes.addView(botao("Estoque", () -> { ref[0].dismiss(); ajustarEstoque(p); }), new LinearLayout.LayoutParams(0, -2, 1));
        v.addView(acoes);
        LinearLayout acoes2 = new LinearLayout(this);
        acoes2.setPadding(0, dp(6), 0, 0);
        acoes2.addView(botao("Publicar", () -> { ref[0].dismiss(); publicar(p); }), new LinearLayout.LayoutParams(0, -2, 1));
        acoes2.addView(espaco(6));
        acoes2.addView(botao("Excluir", () -> { ref[0].dismiss(); excluir(p); }), new LinearLayout.LayoutParams(0, -2, 1));
        v.addView(acoes2);
        ref[0] = dialogo(p.getNome(), v).setPositiveButton("Fechar", null).show();
    }

    private void ajustarEstoque(final Produto p) {
        final EditText campo = campo("Novo estoque", InputType.TYPE_CLASS_NUMBER);
        campo.setText(String.valueOf(p.getEstoque()));
        LinearLayout v = vertical();
        v.addView(texto("O novo valor será propagado para TODOS os canais.", 13, SUAVE, false));
        v.addView(campo);
        dialogo("Estoque de " + p.getSku(), v)
                .setPositiveButton("Salvar", (d, w) -> {
                    try {
                        int novo = Integer.parseInt(campo.getText().toString().trim());
                        if (novo < 0) throw new NumberFormatException();
                        int antigo = p.getEstoque();
                        p.setEstoque(novo);
                        sync.sincronizarProduto(p);
                        repo.log(Evento.Tipo.CATALOGO, "Estoque de " + p.getSku() + " ajustado de " + antigo + " para " + novo + " e sincronizado.");
                        repo.alterado();
                        toast("Estoque sincronizado em " + repo.anunciosDoProduto(p.getSku()).size() + " canal(is)");
                    } catch (NumberFormatException e) {
                        toast("Valor inválido");
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void publicar(final Produto p) {
        final Canal[] canais = Canal.values();
        String[] nomes = new String[canais.length];
        final boolean[] marcados = new boolean[canais.length];
        for (int i = 0; i < canais.length; i++) {
            nomes[i] = canais[i].getNome() + " (comissão " + String.format(Locale.ROOT, "%.1f%%", canais[i].getComissao() * 100) + ")";
            marcados[i] = true;
        }
        new AlertDialog.Builder(this)
                .setTitle("Publicar " + p.getSku() + " (preço por canal sugerido pela IA)")
                .setMultiChoiceItems(nomes, marcados, (d, i, b) -> marcados[i] = b)
                .setPositiveButton("Publicar", (d, w) -> {
                    List<Canal> sel = new ArrayList<>();
                    for (int i = 0; i < canais.length; i++) if (marcados[i]) sel.add(canais[i]);
                    int ok = sync.publicar(p, sel, true);
                    repo.alterado();
                    toast("Publicado em " + ok + " de " + sel.size() + " canal(is)");
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void excluir(final Produto p) {
        confirmar("Excluir " + p.getSku() + " e remover de todos os canais?", () -> {
            for (Anuncio a : repo.anunciosDoProduto(p.getSku())) sync.despublicar(a);
            repo.getProdutos().remove(p);
            repo.log(Evento.Tipo.CATALOGO, "Produto " + p.getSku() + " excluído.");
            repo.alterado();
        });
    }

    private void formularioProduto(final Produto existente) {
        final EditText sku = campo("SKU *", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final EditText nome = campo("Nome do produto *", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        final EditText marca = campo("Marca", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText custo = campo("Custo (R$) *", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText preco = campo("Preço base (R$) *", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText estoque = campo("Estoque *", InputType.TYPE_CLASS_NUMBER);
        final EditText categoria = campo("Categoria", InputType.TYPE_CLASS_TEXT);
        final EditText ncm = campo("NCM", InputType.TYPE_CLASS_TEXT);
        final EditText descricao = campo("Descrição", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        descricao.setMinLines(3);
        descricao.setMaxLines(8);

        final Canal[] canais = Canal.values();
        final CheckBox[] checks = new CheckBox[canais.length];

        LinearLayout v = vertical();
        v.addView(sku); v.addView(nome); v.addView(marca); v.addView(custo); v.addView(preco); v.addView(estoque);
        TextView ia = botaoPrimario("✦ Preencher com IA", () -> {
            if (nome.getText().toString().trim().isEmpty()) { toast("Informe ao menos o nome do produto"); return; }
            String[] cn = IAService.sugerirCategoriaNcm(nome.getText().toString());
            categoria.setText(cn[0]);
            ncm.setText(cn[1]);
            Produto tmp = new Produto();
            tmp.setSku(sku.getText().toString().trim().toUpperCase(Locale.ROOT));
            tmp.setNome(nome.getText().toString().trim());
            tmp.setMarca(marca.getText().toString().trim());
            descricao.setText(IAService.gerarDescricao(tmp));
            StringBuilder msg = new StringBuilder("Categoria: " + cn[0] + "\nNCM: " + cn[1] + "\nDescrição gerada.");
            try {
                tmp.setCusto(numero(custo.getText().toString()));
                if (preco.getText().toString().trim().isEmpty())
                    preco.setText(String.format(Locale.ROOT, "%.2f", IAService.sugerirPreco(tmp, Canal.LOJA_PROPRIA, IAService.MARGEM_ALVO)));
                msg.append("\n\nPreço sugerido por canal (margem 20%):");
                for (Canal ch : canais) msg.append("\n• ").append(ch.getNome()).append(": ")
                        .append(moeda.format(IAService.sugerirPreco(tmp, ch, IAService.MARGEM_ALVO)));
            } catch (Exception e) {
                msg.append("\n\nInforme o custo para receber sugestão de preço.");
            }
            repo.log(Evento.Tipo.IA, "IA sugeriu cadastro para '" + tmp.getNome() + "' (" + cn[0] + ", NCM " + cn[1] + ")");
            new AlertDialog.Builder(this).setTitle("Assistente IA").setMessage(msg).setPositiveButton("OK", null).show();
        });
        LinearLayout.LayoutParams lpIa = new LinearLayout.LayoutParams(-1, -2);
        lpIa.topMargin = dp(8);
        lpIa.bottomMargin = dp(4);
        v.addView(ia, lpIa);
        v.addView(categoria); v.addView(ncm); v.addView(descricao);
        v.addView(espacoV(8));
        v.addView(texto("Publicar em:", 14, VERDE_ESCURO, true));
        for (int i = 0; i < canais.length; i++) {
            checks[i] = new CheckBox(this);
            checks[i].setText(canais[i].getNome());
            checks[i].setChecked(existente == null || repo.anuncio(existente.getSku(), canais[i]) != null);
            v.addView(checks[i]);
        }

        if (existente != null) {
            sku.setText(existente.getSku());
            sku.setEnabled(false);
            nome.setText(existente.getNome());
            marca.setText(existente.getMarca());
            custo.setText(String.format(Locale.ROOT, "%.2f", existente.getCusto()));
            preco.setText(String.format(Locale.ROOT, "%.2f", existente.getPreco()));
            estoque.setText(String.valueOf(existente.getEstoque()));
            categoria.setText(existente.getCategoria());
            ncm.setText(existente.getNcm());
            descricao.setText(existente.getDescricao());
        }

        final AlertDialog d = dialogo(existente == null ? "Novo produto" : "Editar " + existente.getSku(), v)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String s = sku.getText().toString().trim().toUpperCase(Locale.ROOT);
            String n = nome.getText().toString().trim();
            if (s.isEmpty() || n.isEmpty()) { toast("SKU e nome são obrigatórios"); return; }
            if (existente == null && repo.produto(s) != null) { toast("Já existe produto com este SKU"); return; }
            double vPreco, vCusto;
            int vEst;
            try {
                vPreco = numero(preco.getText().toString());
                vCusto = numero(custo.getText().toString());
                vEst = Integer.parseInt(estoque.getText().toString().trim());
                if (vPreco <= 0 || vCusto < 0 || vEst < 0) throw new NumberFormatException();
            } catch (Exception e) {
                toast("Verifique preço, custo e estoque");
                return;
            }
            Produto p = existente != null ? existente : new Produto();
            p.setSku(s);
            p.setNome(n);
            p.setMarca(marca.getText().toString().trim());
            p.setPreco(vPreco);
            p.setCusto(vCusto);
            p.setEstoque(vEst);
            String[] cn = IAService.sugerirCategoriaNcm(n);
            String cat = categoria.getText().toString().trim();
            String nc = ncm.getText().toString().trim();
            p.setCategoria(cat.isEmpty() ? cn[0] : cat);
            p.setNcm(nc.isEmpty() ? cn[1] : nc);
            if (p.getEan() == null) p.setEan("");
            String desc = descricao.getText().toString();
            p.setDescricao(desc.trim().isEmpty() ? IAService.gerarDescricao(p) : desc);
            if (existente == null) repo.getProdutos().add(p);
            repo.log(Evento.Tipo.CATALOGO, "Produto " + p.getSku() + (existente == null ? " cadastrado." : " atualizado."));
            List<Canal> sel = new ArrayList<>();
            for (int i = 0; i < canais.length; i++) {
                Anuncio a = repo.anuncio(p.getSku(), canais[i]);
                if (checks[i].isChecked()) sel.add(canais[i]);
                else if (a != null) sync.despublicar(a);
            }
            int ok = sel.isEmpty() ? 0 : sync.publicar(p, sel, true);
            d.dismiss();
            repo.alterado();
            toast("Salvo e publicado em " + ok + " canal(is)");
        }));
        d.show();
    }

    /** Mesmo conteúdo de exemplo-importacao.csv: demonstra o cadastro em massa. */
    private void importarExemplo() {
        final String[] csv = {
                "MOU-011;Mouse Sem Fio Ergonômico;Clickr;89,90;28,00;20",
                "TEC-012;Teclado Mecânico RGB ABNT2;Clickr;249,90;95,00;10",
                "PER-013;Perfume Amadeirado 100ml;Essenza;189,90;60,00;15",
                "BOL-014;Bola de Futebol Oficial;GolPro;129,90;45,00;3"};
        confirmar("Importar 4 produtos da planilha de exemplo e publicar em todos os canais?\n\n"
                + "Categoria, NCM e descrição serão preenchidos pela IA.", () -> {
            int novos = 0;
            for (String linha : csv) {
                String[] c = linha.split(";");
                Produto p = repo.produto(c[0]);
                boolean novo = p == null;
                if (novo) p = new Produto();
                p.setSku(c[0]); p.setNome(c[1]); p.setMarca(c[2]);
                p.setPreco(numero(c[3])); p.setCusto(numero(c[4])); p.setEstoque(Integer.parseInt(c[5]));
                String[] cn = IAService.sugerirCategoriaNcm(p.getNome());
                p.setCategoria(cn[0]); p.setNcm(cn[1]); p.setEan("");
                p.setDescricao(IAService.gerarDescricao(p));
                if (novo) { repo.getProdutos().add(p); novos++; }
                sync.publicar(p, Arrays.asList(Canal.values()), true);
            }
            repo.log(Evento.Tipo.CATALOGO, "Importação de planilha: " + novos + " novo(s), " + (csv.length - novos) + " atualizado(s).");
            repo.alterado();
            toast(csv.length + " produtos importados e publicados");
        });
    }

    // =====================================================================
    // CANAIS
    // =====================================================================
    private void telaCanais() {
        secao("Conexões com marketplaces (simuladas)");
        for (final Canal c : Canal.values()) {
            LinearLayout box = card();
            box.setBackground(faixaLateralCard(c.getCor()));
            LinearLayout linha = new LinearLayout(this);
            linha.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout info = vertical();
            info.addView(texto(c.getNome(), 15, TEXTO, true));
            info.addView(texto(String.format(Locale.ROOT, "Comissão %.1f%% + %s • título até %d caract.",
                    c.getComissao() * 100, moeda.format(c.getTaxaFixa()), c.getMaxTitulo()), 11, SUAVE, false));
            linha.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            Switch sw = new Switch(this);
            sw.setChecked(repo.conectado(c));
            sw.setOnCheckedChangeListener((b, on) -> alternarConexao(c, on));
            linha.addView(sw);
            box.addView(linha);
            conteudo.addView(box);
        }

        LinearLayout falha = card();
        final TextView rotulo = texto("", 13, TEXTO, false);
        final SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress((int) Math.round(MockMarketplaceConnector.taxaFalha * 100));
        rotulo.setText("Falha simulada das APIs: " + sb.getProgress() + "%");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) {
                MockMarketplaceConnector.taxaFalha = v / 100.0;
                rotulo.setText("Falha simulada das APIs: " + v + "%");
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        falha.addView(rotulo);
        falha.addView(sb);
        conteudo.addView(falha);

        botoes(botaoPrimario("Sincronizar tudo", () -> {
            int[] r = sync.sincronizarTudo();
            repo.alterado();
            toast(r[0] + " ok, " + r[1] + " com erro/pendente");
        }), botao("Reprocessar erros", () -> {
            int n = sync.reprocessarErros();
            repo.alterado();
            toast(n + " anúncio(s) recuperado(s)");
        }));

        secao("Anúncios");
        filtroCanais(filtroAnuncios, c -> { filtroAnuncios = c; renderizar(); });
        List<Anuncio> lista = new ArrayList<>(repo.getAnuncios());
        Collections.sort(lista, (a, b) -> a.getCanal() != b.getCanal()
                ? a.getCanal().ordinal() - b.getCanal().ordinal() : a.getSku().compareTo(b.getSku()));
        int n = 0;
        for (final Anuncio a : lista) {
            if (filtroAnuncios != null && a.getCanal() != filtroAnuncios) continue;
            if (n++ >= MAX_ITENS_LISTA) break;
            Produto p = repo.produto(a.getSku());
            LinearLayout box = card();
            box.setBackground(faixaLateralCard(a.getCanal().getCor()));
            LinearLayout topo = new LinearLayout(this);
            topo.addView(texto(a.getCanal().getNome() + " • " + a.getSku(), 13, SUAVE, true), new LinearLayout.LayoutParams(0, -2, 1));
            topo.addView(etiqueta(a.getStatus().name(), corStatus(a.getStatus().name())));
            box.addView(topo);
            box.addView(texto(a.getTitulo(), 14, TEXTO, false));
            box.addView(texto(moeda.format(a.getPreco()) + " • estoque no canal " + a.getEstoquePublicado()
                    + " / central " + (p == null ? "-" : p.getEstoque())
                    + (a.getIdExterno() != null ? " • " + a.getIdExterno() : ""), 12, SUAVE, false));
            if (a.getStatus() != Anuncio.Status.SINCRONIZADO)
                box.addView(texto(a.getMensagem(), 12, corStatus(a.getStatus().name()), false));
            box.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(a.getCanal().getNome() + " • " + a.getSku())
                    .setItems(new String[]{a.getStatus() == Anuncio.Status.PAUSADO ? "Reativar anúncio" : "Pausar anúncio",
                            "Remover anúncio do canal"}, (d, w) -> {
                        if (w == 0) sync.pausarOuAtivar(a);
                        else sync.despublicar(a);
                        repo.alterado();
                    }).show());
            conteudo.addView(box);
        }
        if (n == 0) conteudo.addView(texto("Nenhum anúncio.", 14, SUAVE, false));
    }

    private void alternarConexao(Canal c, boolean conectar) {
        if (repo.conectado(c) == conectar) return;
        repo.setConectado(c, conectar);
        if (conectar) {
            repo.log(Evento.Tipo.SYNC, c + " reconectado. Reprocessando fila pendente...");
            int n = sync.reprocessarErros();
            toast(c + " reconectado: " + n + " anúncio(s) sincronizado(s)");
        } else {
            repo.log(Evento.Tipo.SYNC, c + " desconectado. Alterações ficarão pendentes até a reconexão.");
            for (Anuncio a : repo.getAnuncios()) {
                if (a.getCanal() == c && a.getStatus() != Anuncio.Status.PAUSADO) {
                    a.setStatus(Anuncio.Status.PENDENTE);
                    a.setMensagem("Canal desconectado - aguardando reconexão");
                }
            }
            toast(c + " desconectado");
        }
        repo.alterado();
    }

    // =====================================================================
    // PEDIDOS
    // =====================================================================
    private void telaPedidos() {
        botoes(botaoPrimario("Simular pedido", this::simularUm),
                botao(simulacaoAuto ? "■ Parar automático" : "▶ Automático (3s)", this::alternarSimulacao));
        LinearLayout nf = card();
        LinearLayout linha = new LinearLayout(this);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        linha.addView(texto("Emitir NF-e automaticamente", 14, TEXTO, true), new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(repo.isFaturamentoAutomatico());
        sw.setOnCheckedChangeListener((b, on) -> {
            repo.setFaturamentoAutomatico(on);
            repo.log(Evento.Tipo.FISCAL, "Faturamento automático " + (on ? "ativado." : "desativado."));
            repo.alterado();
        });
        linha.addView(sw);
        nf.addView(linha);
        nf.addView(texto("Pedido recebido → baixa no estoque central → estoque propagado a todos os canais → NF-e emitida.",
                12, SUAVE, false));
        conteudo.addView(nf);

        filtroCanais(filtroPedidos, c -> { filtroPedidos = c; renderizar(); });
        List<Pedido> lista = new ArrayList<>(repo.getPedidos());
        Collections.sort(lista, (a, b) -> b.getNumero() - a.getNumero());
        int n = 0;
        for (final Pedido p : lista) {
            if (filtroPedidos != null && p.getCanal() != filtroPedidos) continue;
            if (n++ >= MAX_ITENS_LISTA) break;
            LinearLayout box = card();
            box.setBackground(faixaLateralCard(p.getCanal().getCor()));
            LinearLayout topo = new LinearLayout(this);
            topo.addView(texto("#" + p.getNumero() + " • " + p.getCanal().getNome(), 15, TEXTO, true), new LinearLayout.LayoutParams(0, -2, 1));
            topo.addView(etiqueta(p.getStatus().name(), corStatus(p.getStatus().name())));
            box.addView(topo);
            box.addView(texto(p.getCliente() + " • " + p.getCidadeUf(), 13, SUAVE, false));
            box.addView(texto(moeda.format(p.getTotal()) + " • " + p.getQuantidadeItens() + " item(ns)"
                    + (p.getNumeroNota() != null ? " • NF-e " + p.getNumeroNota() : "") + " • " + dataHora.format(p.getData()),
                    13, TEXTO, false));
            box.setOnClickListener(v -> detalhePedido(p));
            conteudo.addView(box);
        }
        if (n == 0) conteudo.addView(texto("Nenhum pedido.", 14, SUAVE, false));
    }

    private void detalhePedido(final Pedido p) {
        StringBuilder s = new StringBuilder();
        s.append(p.getCanal()).append(" • ").append(p.getCodigoCanal()).append("\n");
        s.append(dataHora.format(p.getData())).append("\n\n");
        s.append(p.getCliente()).append("\nCPF ").append(p.getCpf()).append("\n").append(p.getCidadeUf()).append("\n\n");
        for (ItemPedido i : p.getItens())
            s.append(i.getQuantidade()).append("x ").append(i.getDescricao()).append("\n   ")
                    .append(moeda.format(i.getPrecoUnitario())).append(" = ").append(moeda.format(i.getTotal())).append("\n");
        s.append("\nTOTAL: ").append(moeda.format(p.getTotal()));
        s.append("\nComissão estimada: ").append(moeda.format(p.getTotal() * p.getCanal().getComissao() + p.getCanal().getTaxaFixa()));
        s.append("\nStatus: ").append(p.getStatus());
        if (p.getNumeroNota() != null) s.append(" • NF-e nº ").append(p.getNumeroNota());

        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Pedido #" + p.getNumero()).setMessage(s)
                .setNeutralButton("Fechar", null);
        if (p.getStatus() == Pedido.Status.RECEBIDO) {
            b.setPositiveButton("Emitir NF-e", (d, w) -> {
                NotaFiscal nf = pedidos.faturar(p);
                repo.alterado();
                if (nf != null) toast("NF-e " + nf.getNumero() + " autorizada");
            });
        } else if (p.getStatus() == Pedido.Status.FATURADO) {
            b.setPositiveButton("Marcar enviado", (d, w) -> { pedidos.marcarEnviado(p); repo.alterado(); });
        }
        if (p.getStatus() != Pedido.Status.CANCELADO) {
            b.setNegativeButton("Cancelar pedido", (d, w) -> confirmar(
                    "Cancelar o pedido? O estoque volta para todos os canais e a NF-e é cancelada.", () -> {
                        pedidos.cancelar(p);
                        repo.alterado();
                        toast("Pedido cancelado e estoque devolvido");
                    }));
        }
        b.show();
    }

    // =====================================================================
    // NOTAS
    // =====================================================================
    private void telaNotas() {
        conteudo.addView(texto("Ambiente de homologação simulado — notas sem valor fiscal.", 12, SUAVE, false));
        conteudo.addView(espacoV(6));
        int n = 0;
        for (int i = repo.getNotas().size() - 1; i >= 0 && n < MAX_ITENS_LISTA; i--, n++) {
            final NotaFiscal nf = repo.getNotas().get(i);
            LinearLayout box = card();
            LinearLayout topo = new LinearLayout(this);
            topo.addView(texto("NF-e " + nf.getNumero() + " • Pedido #" + nf.getPedidoNumero(), 15, TEXTO, true),
                    new LinearLayout.LayoutParams(0, -2, 1));
            topo.addView(etiqueta(nf.getStatus().name(), corStatus(nf.getStatus().name())));
            box.addView(topo);
            box.addView(texto(nf.getCanal().getNome() + " • " + nf.getCliente() + " • " + moeda.format(nf.getValorProdutos()), 13, SUAVE, false));
            TextView chave = texto(FiscalService.chaveFormatada(nf.getChave()), 11, TEXTO, false);
            chave.setTypeface(Typeface.MONOSPACE);
            box.addView(chave);
            box.setOnClickListener(v -> mostrarDocumento("DANFE — NF-e " + nf.getNumero(), FiscalService.gerarDanfe(nf), nf));
            conteudo.addView(box);
        }
        if (n == 0) conteudo.addView(texto("Nenhuma nota emitida ainda.", 14, SUAVE, false));
    }

    private void mostrarDocumento(String titulo, String doc, final NotaFiscal nf) {
        TextView t = new TextView(this);
        t.setText(doc);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(9);
        t.setTextColor(TEXTO);
        t.setTextIsSelectable(true);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.addView(t);
        ScrollView s = new ScrollView(this);
        s.addView(h);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(titulo).setView(s).setNeutralButton("Fechar", null);
        if (nf != null) {
            b.setNegativeButton("Ver XML", (d, w) -> mostrarDocumento("XML — NF-e " + nf.getNumero(), FiscalService.gerarXml(nf), null));
            b.setPositiveButton("Compartilhar", (d, w) -> compartilhar(nf));
        }
        b.show();
    }

    private void compartilhar(NotaFiscal nf) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "NF-e " + nf.getNumero() + " (simulada)");
        i.putExtra(Intent.EXTRA_TEXT, FiscalService.gerarDanfe(nf) + "\n\n" + FiscalService.gerarXml(nf));
        startActivity(Intent.createChooser(i, "Compartilhar NF-e"));
    }

    // =====================================================================
    // LOG
    // =====================================================================
    private void telaLog() {
        botoes(botao("Limpar log", () -> { repo.getEventos().clear(); repo.alterado(); }));
        List<Evento> ev = repo.getEventos();
        int n = 0;
        LinearLayout box = card();
        for (int i = ev.size() - 1; i >= 0 && n < 200; i--, n++) {
            Evento e = ev.get(i);
            int cor = e.getTipo() == Evento.Tipo.ERRO ? VERMELHO : e.getTipo() == Evento.Tipo.FISCAL ? AZUL
                    : e.getTipo() == Evento.Tipo.PEDIDO ? VERDE : e.getTipo() == Evento.Tipo.IA ? 0xFF7B1FA2 : TEXTO;
            TextView t = texto(dataHora.format(e.getData()) + "  [" + e.getTipo() + "]\n" + e.getMensagem(), 12, cor, false);
            t.setPadding(0, dp(4), 0, dp(4));
            box.addView(t);
        }
        if (n == 0) box.addView(texto("Log vazio.", 14, SUAVE, false));
        conteudo.addView(box);
    }

    // =====================================================================
    // helpers de UI
    // =====================================================================
    private interface AoEscolherCanal { void escolher(Canal c); }

    private void filtroCanais(Canal atual, final AoEscolherCanal acao) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout l = new LinearLayout(this);
        l.addView(chip("Todos", atual == null, () -> acao.escolher(null)));
        for (final Canal c : Canal.values()) l.addView(chip(c.getNome(), atual == c, () -> acao.escolher(c)));
        hs.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        conteudo.addView(hs, lp);
    }

    private TextView chip(String t, boolean sel, final Runnable r) {
        TextView c = texto(t, 13, sel ? Color.WHITE : TEXTO, sel);
        c.setPadding(dp(12), dp(6), dp(12), dp(6));
        c.setBackground(fundo(sel ? VERDE_APP : Color.WHITE, 16, sel ? 0 : BORDA));
        c.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        c.setLayoutParams(lp);
        return c;
    }

    private void secao(String t) {
        TextView s = texto(t, 16, VERDE_ESCURO, true);
        s.setPadding(0, dp(14), 0, dp(6));
        conteudo.addView(s);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(fundo(Color.WHITE, 12, BORDA));
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    private View cardKpi(String titulo, String valor, String sub) {
        LinearLayout c = card();
        c.setBackground(faixaLateralCard(0x00966E));
        c.addView(texto(titulo, 12, SUAVE, false));
        TextView v = texto(valor, 20, TEXTO, true);
        v.setSingleLine(true);
        c.addView(v);
        c.addView(texto(sub, 11, SUAVE, false));
        return c;
    }

    private void linhaCards(View a, View b) {
        LinearLayout l = new LinearLayout(this);
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(0, -2, 1);
        pa.rightMargin = dp(4);
        l.addView(a, pa);
        if (b != null) {
            LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, -2, 1);
            pb.leftMargin = dp(4);
            l.addView(b, pb);
        }
        conteudo.addView(l);
    }

    private void botoes(TextView... bs) {
        LinearLayout l = new LinearLayout(this);
        l.setPadding(0, dp(2), 0, dp(8));
        for (int i = 0; i < bs.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.leftMargin = dp(8);
            l.addView(bs[i], lp);
        }
        conteudo.addView(l);
    }

    private TextView botao(String t, final Runnable r) {
        TextView b = texto(t, 14, VERDE_ESCURO, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(11), dp(10), dp(11));
        b.setBackground(fundo(Color.WHITE, 10, VERDE_APP));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private TextView botaoPrimario(String t, final Runnable r) {
        TextView b = botao(t, r);
        b.setTextColor(Color.WHITE);
        b.setBackground(fundo(VERDE_APP, 10, 0));
        return b;
    }

    private TextView etiqueta(String t, int cor) {
        TextView e = texto(t, 11, cor, true);
        e.setPadding(dp(8), dp(2), dp(8), dp(2));
        e.setBackground(fundo((cor & 0x00FFFFFF) | 0x22000000, 10, 0));
        return e;
    }

    private TextView texto(String t, float sp, int cor, boolean negrito) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(cor);
        if (negrito) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private EditText campo(String dica, int tipo) {
        EditText e = new EditText(this);
        e.setHint(dica);
        e.setInputType(tipo);
        e.setTextSize(15);
        return e;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private View espaco(int largura) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(largura), 1));
        return v;
    }

    private View espacoV(int altura) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(altura)));
        return v;
    }

    private AlertDialog.Builder dialogo(String titulo, View corpo) {
        corpo.setPadding(dp(20), dp(8), dp(20), dp(8));
        ScrollView s = new ScrollView(this);
        s.addView(corpo, new ViewGroup.LayoutParams(-1, -2));
        return new AlertDialog.Builder(this).setTitle(titulo).setView(s);
    }

    private void confirmar(String msg, final Runnable sim) {
        new AlertDialog.Builder(this).setMessage(msg)
                .setPositiveButton("Sim", (d, w) -> sim.run())
                .setNegativeButton("Não", null).show();
    }

    private GradientDrawable fundo(int cor, int raio, int borda) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(raio));
        if (borda != 0) g.setStroke(dp(1), borda);
        return g;
    }

    /** Card branco com uma faixa colorida à esquerda (cor do canal). */
    private android.graphics.drawable.Drawable faixaLateralCard(int rgb) {
        GradientDrawable faixa = fundo(0xFF000000 | rgb, 12, 0);
        GradientDrawable branco = fundo(Color.WHITE, 12, BORDA);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{faixa, branco});
        ld.setLayerInset(1, dp(5), 0, 0, 0);
        return ld;
    }

    private android.graphics.drawable.Drawable faixaLateral(int rgb) {
        GradientDrawable faixa = fundo(0xFF000000 | rgb, 6, 0);
        GradientDrawable claro = fundo(0xFFF7F9FA, 6, 0);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{faixa, claro});
        ld.setLayerInset(1, dp(4), 0, 0, 0);
        return ld;
    }

    private int corStatus(String s) {
        switch (s) {
            case "SINCRONIZADO": case "AUTORIZADA": case "ENVIADO": case "OK": return VERDE;
            case "ERRO": case "CANCELADA": case "CANCELADO": case "SEM ESTOQUE": return VERMELHO;
            case "PENDENTE": case "RECEBIDO": case "BAIXO": return LARANJA;
            case "FATURADO": return AZUL;
            case "PAUSADO": return CINZA;
            default: return TEXTO;
        }
    }

    private static double numero(String s) {
        return Double.parseDouble(s.trim().replace("R$", "").replace(" ", "").replace(",", "."));
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
