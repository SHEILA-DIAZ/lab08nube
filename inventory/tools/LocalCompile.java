import javax.tools.*;
import java.util.*;
public class LocalCompile {
 public static void main(String[] args) {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  if(compiler==null) throw new IllegalStateException("Se requiere un JDK");
  StandardJavaFileManager files=compiler.getStandardFileManager(null,null,java.nio.charset.StandardCharsets.UTF_8);
  var sources=files.getJavaFileObjectsFromStrings(Arrays.asList(args).subList(2,args.length));
  var options=List.of("--release","17","-parameters","-encoding","UTF-8","-classpath",args[0],
    "-processor","lombok.launch.AnnotationProcessorHider$AnnotationProcessor","-d",args[1]);
  boolean ok=compiler.getTask(null,files,null,options,null,sources).call();
  // This short-lived process releases handles on exit. Avoid ZipFS real-path cleanup
  // denied by the managed Windows environment when fileManager.close() is called.
  if(!ok)System.exit(1);
 }
}