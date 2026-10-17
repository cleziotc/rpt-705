import br.com.pp5ci.repeaters705.Csv;
import java.nio.file.*;
import java.util.*;

public class CsvTest {
 static int checks;
 static void check(boolean v,String label){checks++;if(!v)throw new AssertionError(label);}
 static void rejects(Runnable r,String label){try{r.run();throw new AssertionError("Expected rejection: "+label);}catch(IllegalArgumentException ok){checks++;}}
 public static void main(String[] args)throws Exception {
  String input=Files.readString(Path.of(args[0]));List<String[]> rows=Csv.parse(input);
  check(rows.size()==589,"589 records");check(rows.stream().filter(r->r[9].equals("DV")).count()==41,"41 DSTAR");
  check(Csv.write(rows,';').equals(input+"\r\n"),"semicolon export preserves every original byte and appends final CRLF");
  List<String[]> comma=Csv.parse("\ufeff"+Csv.write(rows,','));check(comma.size()==589,"BOM comma import");
  for(int i=0;i<rows.size();i++)check(Arrays.equals(rows.get(i),comma.get(i)),"roundtrip "+i);
  String[] quoted=rows.get(0).clone();quoted[2]="Rio; \"Branco\"";List<String[]> special=Collections.singletonList(quoted);
  check(Arrays.equals(quoted,Csv.parse(Csv.write(special,';')).get(0)),"quoted delimiters and escapes");
  String[] bad=rows.get(0).clone();bad[6]="NaN";rejects(()->Csv.validate(bad),"NaN");bad[6]="146.5200000";rejects(()->Csv.validate(bad),"precision");bad[6]="146.520000";bad[14]="-91";rejects(()->Csv.validate(bad),"latitude");bad[14]="-20";bad[0]="51";rejects(()->Csv.validate(bad),"group count");
  rejects(()->Csv.parse(input+input.split("\r\n")[1]+"\r\n"),"duplicate CSV");
  rejects(()->Csv.parse(input.replace("Group No;","Other;")),"missing header");
  rejects(()->Csv.parse(input+"\"unclosed"),"unclosed quotes");
  List<String[]> conflicting=new ArrayList<>();conflicting.add(rows.get(0));String[] conflict=rows.get(1).clone();conflict[0]="1";conflicting.add(conflict);rejects(()->Csv.validateDatabase(conflicting),"inconsistent group names");
  List<String[]> empty=new ArrayList<>();check(Csv.write(empty,';').startsWith("Group No;"),"empty local database remains valid");
  System.out.println("PASS: "+checks+" CSV checks; semicolon export preserves all input fields and CRLF.");
 }
}
