import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.OutputDirectoryProvider;
import java.nio.file.*;
import java.util.*;
public class LocalTestRunner {
 public static void main(String[] args) throws Exception {
  ConfigurationParameters parameters=new ConfigurationParameters() {
   public Optional<String> get(String key){return Optional.empty();}
   public Optional<Boolean> getBoolean(String key){return Optional.empty();}
   public Set<String> keySet(){return Set.of();}
  };
  OutputDirectoryProvider output=new OutputDirectoryProvider() {
   public Path getRootDirectory(){return Path.of("target/local-test-reports");}
   public Path createOutputDirectory(TestDescriptor d)throws java.io.IOException{return Files.createDirectories(getRootDirectory());}
  };
  EngineDiscoveryRequest request=new EngineDiscoveryRequest() {
   public <T extends DiscoverySelector> List<T> getSelectorsByType(Class<T> type){
    List<T> list=new ArrayList<>();for(String n:args){var s=DiscoverySelectors.selectClass(n);if(type.isInstance(s))list.add(type.cast(s));}return list;
   }
   public <T extends DiscoveryFilter<?>> List<T> getFiltersByType(Class<T> type){return List.of();}
   public ConfigurationParameters getConfigurationParameters(){return parameters;}
   public OutputDirectoryProvider getOutputDirectoryProvider(){return output;}
   public OutputDirectoryCreator getOutputDirectoryCreator(){return output;}
  };
  int[] counts={0,0};
  EngineExecutionListener listener=new EngineExecutionListener(){
   public void executionFinished(TestDescriptor d,TestExecutionResult r){
    if(d.isTest()){counts[0]++;System.out.println("TEST "+r.getStatus()+" "+d.getDisplayName());}
    if(r.getStatus()==TestExecutionResult.Status.FAILED){counts[1]++;r.getThrowable().ifPresent(Throwable::printStackTrace);}
   }
  };
  JupiterTestEngine engine=new JupiterTestEngine();
  var root=engine.discover(request,UniqueId.forEngine(engine.getId()));
  engine.execute(ExecutionRequest.create(root,listener,parameters,output,
   new org.junit.platform.engine.support.store.NamespacedHierarchicalStore<>(new org.junit.platform.engine.support.store.NamespacedHierarchicalStore<>(null)),
   org.junit.platform.engine.CancellationToken.create()));
  String summary="Tests executed: "+counts[0]+", failures: "+counts[1];
  System.out.println(summary);Files.createDirectories(output.getRootDirectory());Files.writeString(output.getRootDirectory().resolve("summary.txt"),summary);
  if(counts[0]==0||counts[1]>0)System.exit(1);
 }
}
