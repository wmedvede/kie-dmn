package org.kie.dmn.core.compiler;

import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNType;
import org.kie.dmn.api.core.ast.DMNNode;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEventListener;
import org.kie.dmn.core.impl.BaseDMNTypeImpl;
import org.kie.dmn.core.impl.DMNModelImpl;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.CompiledExpression;
import org.kie.dmn.feel.lang.CompilerContext;
import org.kie.dmn.feel.lang.Type;
import org.kie.dmn.feel.lang.types.BuiltInType;
import org.kie.dmn.feel.runtime.UnaryTest;
import org.kie.dmn.feel.runtime.events.SyntaxErrorEvent;
import org.kie.dmn.model.v1_1.DMNElement;

import java.util.*;

public class DMNFEELHelper
        implements FEELEventListener {

    private final FEEL             feel;
    private final Queue<FEELEvent> feelEvents;

    public DMNFEELHelper() {
        this.feelEvents = new LinkedList<>();
        this.feel = createFEELInstance();
    }

    private FEEL createFEELInstance() {
        FEEL feel = FEEL.newInstance();
        feel.addListener( this );
        return feel;
    }

    @Override
    public void onEvent(FEELEvent event) {
        feelEvents.add( event );
    }

    public CompiledExpression compileFeelExpression(DMNCompilerContext ctx, String expression, DMNModelImpl model, DMNElement element, String errorMsg) {
        CompilerContext feelctx = feel.newCompilerContext();

        for( Map.Entry<String, DMNType> entry : ctx.getVariables().entrySet() ) {
            feelctx.addInputVariableType( entry.getKey(), ((BaseDMNTypeImpl)entry.getValue()).getFeelType() );
        }
        CompiledExpression ce = feel.compile( expression, feelctx );
        processEvents( model, element, errorMsg );
        return ce;
    }

    public List<UnaryTest> evaluateUnaryTests(DMNCompilerContext ctx, String unaryTests, DMNModelImpl model, DMNElement element, String errorMsg) {
        Map<String, Type> variableTypes = new HashMap<>(  );
        for( Map.Entry<String, DMNType> entry : ctx.getVariables().entrySet() ) {
            // TODO: need to properly resolve types here
            variableTypes.put( entry.getKey(), BuiltInType.UNKNOWN );
        }
        List<UnaryTest> result = feel.evaluateUnaryTests( unaryTests, variableTypes );
        processEvents( model, element, errorMsg );
        return result;
    }

    public void processEvents(DMNModelImpl model, DMNElement element, String msg) {
        while ( !feelEvents.isEmpty() ) {
            FEELEvent event = feelEvents.remove();
            if ( !isDuplicateEvent( model, event, msg ) ) {
                if ( event instanceof SyntaxErrorEvent ) {
                    String errorMsg = msg + ": invalid syntax";
                    model.addMessage( DMNMessage.Severity.ERROR, errorMsg, element, event );
                } else if ( event.getSeverity() == FEELEvent.Severity.ERROR ) {
                    model.addMessage( DMNMessage.Severity.ERROR, msg + ": " + event.getMessage(), element );
                }
            }
        }
    }

    private boolean isDuplicateEvent(DMNModelImpl model, FEELEvent event, String errorMsg) {
        return model.getMessages().stream().anyMatch( msg -> msg.getMessage().startsWith( errorMsg ) );
    }

}
