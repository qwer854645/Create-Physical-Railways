package com.guidedrailways;
import java.util.List;
import java.util.function.Consumer;
/** Validate every target before mutating anything; undo even a partially applied failing move. */
public final class MoveBatch {
    private MoveBatch(){}
    public static <T> void commit(List<T> moves,Consumer<T> validate,Consumer<T> apply,Consumer<T> restore){
        var plan=List.copyOf(moves);
        for(var move:plan)validate.accept(move);
        int attempted=0;
        try{for(var move:plan){attempted++;apply.accept(move);}}
        catch(RuntimeException error){
            for(int i=attempted-1;i>=0;i--)try{restore.accept(plan.get(i));}catch(RuntimeException rollback){error.addSuppressed(rollback);}
            throw error;
        }
    }
}
