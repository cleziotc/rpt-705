package br.com.pp5ci.repeaters705;

import java.util.*;
import java.math.BigDecimal;

/** Lossless IC-705 CSV codec. No Android dependencies; also exercised on the JVM. */
public final class Csv {
    public static final String[] HEADERS={"Group No","Group Name","Name","Sub Name","Repeater Call Sign","Gateway Call Sign","Frequency","Dup","Offset","Mode","TONE","Repeater Tone","RPT1USE","Position","Latitude","Longitude","UTC Offset"};
    public static final String[] TONES={"67.0Hz","69.3Hz","71.9Hz","74.4Hz","77.0Hz","79.7Hz","82.5Hz","85.4Hz","88.5Hz","91.5Hz","94.8Hz","97.4Hz","100.0Hz","103.5Hz","107.2Hz","110.9Hz","114.8Hz","118.8Hz","123.0Hz","127.3Hz","131.8Hz","136.5Hz","141.3Hz","146.2Hz","151.4Hz","156.7Hz","159.8Hz","162.2Hz","165.5Hz","167.9Hz","171.3Hz","173.8Hz","177.3Hz","179.9Hz","183.5Hz","186.2Hz","189.9Hz","192.8Hz","196.6Hz","199.5Hz","203.5Hz","206.5Hz","210.7Hz","218.1Hz","225.7Hz","229.1Hz","233.6Hz","241.8Hz","250.3Hz","254.1Hz"};
    public static List<String[]> parse(String text) {
        if(text.length()>5_000_000) throw new IllegalArgumentException("CSV excede 5 MB.");
        if(text.startsWith("\ufeff")) text=text.substring(1);
        char delim=detect(text);
        List<List<String>> raw=read(text,delim);
        if(raw.isEmpty()) throw new IllegalArgumentException("CSV vazio.");
        List<String> header=raw.remove(0); int[] order=new int[17];
        if(header.size()!=17) throw new IllegalArgumentException("Cabeçalho precisa conter as 17 colunas do IC-705.");
        Set<String> names=new HashSet<>();
        for(int i=0;i<17;i++){
            order[i]=-1;
            for(int j=0;j<header.size();j++)if(header.get(j).trim().equalsIgnoreCase(HEADERS[i]))order[i]=j;
            if(order[i]<0||!names.add(header.get(order[i]).trim().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Coluna ausente/duplicada: "+HEADERS[i]);
        }
        List<String[]> rows=new ArrayList<>(); Set<String> keys=new HashSet<>();
        for(int n=0;n<raw.size();n++){
            List<String> line=raw.get(n);
            if(line.size()!=17)throw new IllegalArgumentException("Registro "+(n+2)+": esperado 17 campos, recebido "+line.size()+".");
            String[] row=new String[17];for(int i=0;i<17;i++)row[i]=line.get(order[i]);
            try{validate(row);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Registro "+(n+2)+": "+ex.getMessage());}
            if(!keys.add(key(row)))throw new IllegalArgumentException("Registro duplicado no CSV: "+row[2]+" / "+row[6]);
            rows.add(row);
        }
        if(rows.isEmpty())throw new IllegalArgumentException("O arquivo não contém repetidoras.");
        validateDatabase(rows); return rows;
    }
    static char detect(String t){int semi=0,comma=0;boolean q=false;for(int i=0;i<t.length();i++){char c=t.charAt(i);if(c=='"')q=!q;if(!q){if(c=='\n'||c=='\r')break;if(c==';')semi++;if(c==',')comma++;}}return semi>=comma?';':',';}
    static List<List<String>> read(String t,char d){
        List<List<String>> out=new ArrayList<>(); List<String> row=new ArrayList<>();StringBuilder v=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<t.length();i++){
            char c=t.charAt(i);
            if(c=='\0')throw new IllegalArgumentException("CSV contém caractere nulo.");
            if(quoted){if(c=='"'){if(i+1<t.length()&&t.charAt(i+1)=='"'){v.append('"');i++;}else{quoted=false;closed=true;}}else v.append(c);continue;}
            if(c=='"'){if(v.length()!=0||closed)throw new IllegalArgumentException("Aspas em posição inválida.");quoted=true;continue;}
            if(c==d){row.add(v.toString());v.setLength(0);closed=false;continue;}
            if(c=='\r'||c=='\n'){if(c=='\r'&&i+1<t.length()&&t.charAt(i+1)=='\n')i++;row.add(v.toString());if(!(row.size()==1&&row.get(0).trim().isEmpty()))out.add(row);row=new ArrayList<>();v.setLength(0);closed=false;continue;}
            if(closed)throw new IllegalArgumentException("Texto após fechamento de aspas.");v.append(c);
        }
        if(quoted)throw new IllegalArgumentException("Campo com aspas não finalizado.");
        if(v.length()>0||!row.isEmpty()||closed){row.add(v.toString());out.add(row);}return out;
    }
    public static void validate(String[] r){
        if(r==null||r.length!=17)throw new IllegalArgumentException("São necessários 17 campos.");
        for(String s:r)if(s==null||s.contains("\n")||s.contains("\r")||s.contains("\0"))throw new IllegalArgumentException("Campos não podem conter quebras de linha.");
        try{int g=Integer.parseInt(r[0]);if(g<1||g>50)throw new Exception();}catch(Exception e){throw new IllegalArgumentException("Grupo deve estar entre 1 e 50.");}
        for(int i:new int[]{1,2,3}){if(r[i].length()>16)throw new IllegalArgumentException(HEADERS[i]+": máximo 16 caracteres.");}
        if(r[1].trim().isEmpty()||r[2].trim().isEmpty())throw new IllegalArgumentException("Grupo e nome são obrigatórios.");
        for(int i:new int[]{4,5})if(r[i].length()>8||!r[i].matches("[ A-Za-z0-9/]*"))throw new IllegalArgumentException(HEADERS[i]+": até 8 caracteres de indicativo.");
        number(r[6],0.030,470.0,"Frequência (MHz)"); number(r[8],0,59.999999,"Offset (MHz)");
        for(int i:new int[]{6,8})if(new BigDecimal(r[i]).scale()>6)throw new IllegalArgumentException(HEADERS[i]+": máximo 6 casas decimais.");
        one(r[7],"Dup","OFF","DUP-","DUP+");one(r[9],"Modo","FM","DV");one(r[10],"TONE","OFF","TONE","TSQL");
        if(!Arrays.asList(TONES).contains(r[11]))throw new IllegalArgumentException("Tom CTCSS inválido. Exemplo: 88.5Hz.");
        one(r[12],"RPT1USE","YES","NO");one(r[13],"Posição","None","Approximate","Exact");
        if(r[9].equals("DV")&&r[4].trim().isEmpty())throw new IllegalArgumentException("D-STAR precisa de indicativo da repetidora.");
        if(!r[13].equals("None")||!r[14].isEmpty()||!r[15].isEmpty()){number(r[14],-90,90,"Latitude");number(r[15],-180,180,"Longitude");}
        if(!r[16].matches("[+-]?(?:0?\\d|1[0-4]):(?:00|15|30|45)"))throw new IllegalArgumentException("Fuso inválido. Exemplo: -3:00.");
    }
    static void number(String s,double min,double max,String label){try{if(!s.matches("-?\\d+(?:\\.\\d+)?"))throw new Exception();double v=Double.parseDouble(s);if(!Double.isFinite(v)||v<min||v>max)throw new Exception();}catch(Exception e){throw new IllegalArgumentException(label+" fora do intervalo "+min+" a "+max+".");}}
    static void one(String s,String label,String... options){if(!Arrays.asList(options).contains(s))throw new IllegalArgumentException(label+" inválido: "+s);}
    public static void validateDatabase(List<String[]> rows){if(rows.size()>2500)throw new IllegalArgumentException("IC-705 aceita até 2500 repetidoras.");Map<String,String> groups=new HashMap<>();Set<String> keys=new HashSet<>();for(String[] r:rows){validate(r);String g=""+Integer.parseInt(r[0]);if(groups.containsKey(g)&&!groups.get(g).equals(r[1]))throw new IllegalArgumentException("Grupo "+g+" possui nomes diferentes.");groups.put(g,r[1]);if(!keys.add(key(r)))throw new IllegalArgumentException("Repetidora duplicada: "+r[2]+" / "+r[6]);}}
    public static String key(String[] r){String call=r[4].trim().toUpperCase(Locale.ROOT);return (call.isEmpty()?r[1]+"|"+r[2]:call)+"|"+new BigDecimal(r[6]).stripTrailingZeros().toPlainString()+"|"+r[9];}
    public static String write(List<String[]> rows,char delimiter){validateDatabase(rows);StringBuilder out=new StringBuilder();append(out,HEADERS,delimiter);for(String[] r:rows)append(out,r,delimiter);return out.toString();}
    static void append(StringBuilder b,String[] row,char d){for(int i=0;i<row.length;i++){if(i>0)b.append(d);String v=row[i];if(v.indexOf(d)>=0||v.contains("\"")||v.contains("\n")||v.contains("\r"))b.append('"').append(v.replace("\"","\"\"")).append('"');else b.append(v);}b.append("\r\n");}
}
