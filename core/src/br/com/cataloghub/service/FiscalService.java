package br.com.cataloghub.service;

import br.com.cataloghub.model.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Emissão fiscal automatizada (SIMULADA). Gera NF-e modelo 55 com chave de acesso de 44 dígitos
 * (com dígito verificador módulo 11), protocolo de autorização fictício, XML e DANFE simplificado.
 * Em produção, este serviço chamaria a SEFAZ ou um emissor (ex.: API de NF-e) com certificado A1.
 */
public class FiscalService {

    public static final String EMITENTE = "LOJA DEMO COMERCIO DIGITAL LTDA";
    public static final String CNPJ = "11222333000181";
    public static final String UF_CODIGO = "35"; // SP
    public static final double ALIQ_ICMS = 0.18, ALIQ_PIS = 0.0165, ALIQ_COFINS = 0.076;

    private final Repositorio repo = Repositorio.get();
    private final Random rnd = new Random();

    public NotaFiscal emitir(Pedido pedido) {
        if (pedido.getNumeroNota() != null) return repo.nota(pedido.getNumeroNota());
        NotaFiscal nf = new NotaFiscal();
        nf.setNumero(repo.novoNumeroNota());
        nf.setPedidoNumero(pedido.getNumero());
        nf.setCanal(pedido.getCanal());
        nf.setCliente(pedido.getCliente());
        nf.setCpf(pedido.getCpf());
        nf.setCidadeUf(pedido.getCidadeUf());
        nf.getItens().addAll(pedido.getItens());
        double v = pedido.getTotal();
        nf.setValorProdutos(v);
        nf.setIcms(arred(v * ALIQ_ICMS));
        nf.setPis(arred(v * ALIQ_PIS));
        nf.setCofins(arred(v * ALIQ_COFINS));
        nf.setChave(gerarChave(nf.getNumero(), nf.getSerie(), nf.getDataEmissao()));
        nf.setProtocolo("1" + UF_CODIGO + new SimpleDateFormat("yy").format(nf.getDataEmissao())
                + String.format("%010d", Math.abs(rnd.nextLong()) % 10000000000L));
        repo.getNotas().add(nf);
        pedido.setNumeroNota(nf.getNumero());
        pedido.setStatus(Pedido.Status.FATURADO);
        repo.log(Evento.Tipo.FISCAL, "NF-e " + nf.getNumero() + " autorizada para pedido #" + pedido.getNumero()
                + " (" + pedido.getCanal() + ") - R$ " + String.format("%.2f", v));
        return nf;
    }

    public void cancelar(NotaFiscal nf) {
        if (nf == null || nf.getStatus() == NotaFiscal.Status.CANCELADA) return;
        nf.setStatus(NotaFiscal.Status.CANCELADA);
        repo.log(Evento.Tipo.FISCAL, "NF-e " + nf.getNumero() + " cancelada (evento 110111).");
    }

    /** cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) DV(1) = 44 dígitos */
    private String gerarChave(int numero, int serie, Date data) {
        String base = UF_CODIGO
                + new SimpleDateFormat("yyMM").format(data)
                + CNPJ
                + "55"
                + String.format("%03d", serie)
                + String.format("%09d", numero)
                + "1"
                + String.format("%08d", rnd.nextInt(100000000));
        return base + dvModulo11(base);
    }

    private static int dvModulo11(String s) {
        int peso = 2, soma = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            soma += (s.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return (resto == 0 || resto == 1) ? 0 : 11 - resto;
    }

    private static double arred(double v) { return Math.round(v * 100.0) / 100.0; }

    public static String chaveFormatada(String chave) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chave.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(chave.charAt(i));
        }
        return sb.toString();
    }

    public static String gerarXml(NotaFiscal nf) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<!-- DOCUMENTO SIMULADO PARA FINS ACADEMICOS - SEM VALOR FISCAL -->\n");
        x.append("<nfeProc versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">\n");
        x.append(" <NFe>\n  <infNFe Id=\"NFe").append(nf.getChave()).append("\" versao=\"4.00\">\n");
        x.append("   <ide>\n    <cUF>").append(UF_CODIGO).append("</cUF>\n    <natOp>VENDA DE MERCADORIA</natOp>\n");
        x.append("    <mod>55</mod>\n    <serie>").append(nf.getSerie()).append("</serie>\n");
        x.append("    <nNF>").append(nf.getNumero()).append("</nNF>\n");
        x.append("    <dhEmi>").append(iso.format(nf.getDataEmissao())).append("</dhEmi>\n");
        x.append("    <tpNF>1</tpNF>\n    <tpAmb>2</tpAmb>\n");
        x.append("    <xPed>").append(nf.getCanal().getNome()).append(" #").append(nf.getPedidoNumero()).append("</xPed>\n   </ide>\n");
        x.append("   <emit>\n    <CNPJ>").append(CNPJ).append("</CNPJ>\n    <xNome>").append(EMITENTE).append("</xNome>\n    <CRT>1</CRT>\n   </emit>\n");
        x.append("   <dest>\n    <CPF>").append(nf.getCpf().replaceAll("\\D", "")).append("</CPF>\n");
        x.append("    <xNome>").append(esc(nf.getCliente())).append("</xNome>\n");
        x.append("    <enderDest><xMun>").append(esc(nf.getCidadeUf())).append("</xMun></enderDest>\n   </dest>\n");
        int n = 1;
        for (ItemPedido i : nf.getItens()) {
            x.append("   <det nItem=\"").append(n++).append("\">\n    <prod>\n");
            x.append("     <cProd>").append(esc(i.getSku())).append("</cProd>\n");
            x.append("     <xProd>").append(esc(i.getDescricao())).append("</xProd>\n");
            x.append("     <NCM>").append(i.getNcm() == null ? "" : i.getNcm().replace(".", "")).append("</NCM>\n");
            x.append("     <CFOP>5102</CFOP>\n     <uCom>UN</uCom>\n");
            x.append("     <qCom>").append(i.getQuantidade()).append("</qCom>\n");
            x.append("     <vUnCom>").append(fmt(i.getPrecoUnitario())).append("</vUnCom>\n");
            x.append("     <vProd>").append(fmt(i.getTotal())).append("</vProd>\n    </prod>\n   </det>\n");
        }
        x.append("   <total><ICMSTot>\n    <vProd>").append(fmt(nf.getValorProdutos())).append("</vProd>\n");
        x.append("    <vICMS>").append(fmt(nf.getIcms())).append("</vICMS>\n");
        x.append("    <vPIS>").append(fmt(nf.getPis())).append("</vPIS>\n");
        x.append("    <vCOFINS>").append(fmt(nf.getCofins())).append("</vCOFINS>\n");
        x.append("    <vNF>").append(fmt(nf.getValorProdutos())).append("</vNF>\n");
        x.append("    <vTotTrib>").append(fmt(nf.getTributosAprox())).append("</vTotTrib>\n   </ICMSTot></total>\n");
        x.append("  </infNFe>\n </NFe>\n");
        x.append(" <protNFe><infProt>\n  <chNFe>").append(nf.getChave()).append("</chNFe>\n");
        x.append("  <nProt>").append(nf.getProtocolo()).append("</nProt>\n");
        x.append("  <cStat>").append(nf.getStatus() == NotaFiscal.Status.AUTORIZADA ? "100" : "101").append("</cStat>\n");
        x.append("  <xMotivo>").append(nf.getStatus() == NotaFiscal.Status.AUTORIZADA
                ? "Autorizado o uso da NF-e" : "Cancelamento de NF-e homologado").append("</xMotivo>\n");
        x.append(" </infProt></protNFe>\n</nfeProc>\n");
        return x.toString();
    }

    public static String gerarDanfe(NotaFiscal nf) {
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        String linha = repetir('=', 78) + "\n";
        String linha2 = repetir('-', 78) + "\n";
        StringBuilder s = new StringBuilder();
        s.append(linha);
        s.append("  DANFE SIMPLIFICADO - DOCUMENTO AUXILIAR DA NOTA FISCAL ELETRÔNICA\n");
        s.append("  *** SIMULAÇÃO ACADÊMICA - SEM VALOR FISCAL ***\n");
        s.append(linha);
        s.append(String.format("  %s%n  CNPJ: %s%n", EMITENTE, "11.222.333/0001-81"));
        s.append(linha2);
        s.append(String.format("  NF-e Nº %09d   Série %03d   Emissão: %s%n", nf.getNumero(), nf.getSerie(), df.format(nf.getDataEmissao())));
        s.append("  Chave de acesso:\n  ").append(chaveFormatada(nf.getChave())).append("\n");
        s.append("  Protocolo: ").append(nf.getProtocolo()).append("   Situação: ").append(nf.getStatus()).append("\n");
        s.append(linha2);
        s.append("  DESTINATÁRIO: ").append(nf.getCliente()).append("   CPF: ").append(nf.getCpf()).append("\n");
        s.append("  Município/UF: ").append(nf.getCidadeUf()).append("\n");
        s.append("  Origem: ").append(nf.getCanal()).append(" - Pedido #").append(nf.getPedidoNumero()).append("\n");
        s.append(linha2);
        s.append(String.format("  %-12s %-34s %4s %11s %11s%n", "CÓDIGO", "DESCRIÇÃO", "QTD", "V.UNIT", "V.TOTAL"));
        for (ItemPedido i : nf.getItens()) {
            String d = i.getDescricao().length() > 34 ? i.getDescricao().substring(0, 34) : i.getDescricao();
            s.append(String.format("  %-12s %-34s %4d %11.2f %11.2f%n", i.getSku(), d, i.getQuantidade(), i.getPrecoUnitario(), i.getTotal()));
        }
        s.append(linha2);
        s.append(String.format("  VALOR TOTAL DA NOTA: R$ %.2f%n", nf.getValorProdutos()));
        s.append(String.format("  Tributos aprox.: ICMS R$ %.2f | PIS R$ %.2f | COFINS R$ %.2f (Lei 12.741/12)%n",
                nf.getIcms(), nf.getPis(), nf.getCofins()));
        s.append(linha);
        return s.toString();
    }

    private static String repetir(char c, int n) {
        char[] a = new char[n];
        Arrays.fill(a, c);
        return new String(a);
    }

    private static String fmt(double v) { return String.format(Locale.US, "%.2f", v); }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
