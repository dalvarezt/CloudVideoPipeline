package ec.ibm.video;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.Disposes;
import javax.ws.rs.core.Application;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;

import ec.ibm.video.context.AppContext;


public class GlobalExecutor extends Application{

    
    @Produces @AppContext
    ManagedExecutor executor = ManagedExecutor.builder()
        .propagated(ThreadContext.APPLICATION)
        .build();

    void disposeExecutor(@Disposes @AppContext ManagedExecutor exec) {
        exec.shutdownNow();
    }

}