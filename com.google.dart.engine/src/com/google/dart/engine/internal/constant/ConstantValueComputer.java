/*
 * Copyright (c) 2013, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.engine.internal.constant;

import com.google.dart.engine.AnalysisEngine;
import com.google.dart.engine.ast.AstNode;
import com.google.dart.engine.ast.CompilationUnit;
import com.google.dart.engine.ast.ConstructorDeclaration;
import com.google.dart.engine.ast.ConstructorFieldInitializer;
import com.google.dart.engine.ast.ConstructorInitializer;
import com.google.dart.engine.ast.DefaultFormalParameter;
import com.google.dart.engine.ast.Expression;
import com.google.dart.engine.ast.FormalParameter;
import com.google.dart.engine.ast.InstanceCreationExpression;
import com.google.dart.engine.ast.NamedExpression;
import com.google.dart.engine.ast.NodeList;
import com.google.dart.engine.ast.SimpleIdentifier;
import com.google.dart.engine.ast.SuperConstructorInvocation;
import com.google.dart.engine.ast.VariableDeclaration;
import com.google.dart.engine.constant.DartObject;
import com.google.dart.engine.constant.DeclaredVariables;
import com.google.dart.engine.element.ConstructorElement;
import com.google.dart.engine.element.Element;
import com.google.dart.engine.element.FieldElement;
import com.google.dart.engine.element.FieldFormalParameterElement;
import com.google.dart.engine.element.ParameterElement;
import com.google.dart.engine.element.VariableElement;
import com.google.dart.engine.error.CompileTimeErrorCode;
import com.google.dart.engine.internal.context.RecordingErrorListener;
import com.google.dart.engine.internal.element.ConstructorElementImpl;
import com.google.dart.engine.internal.element.ParameterElementImpl;
import com.google.dart.engine.internal.element.VariableElementImpl;
import com.google.dart.engine.internal.element.member.ConstructorMember;
import com.google.dart.engine.internal.element.member.ParameterMember;
import com.google.dart.engine.internal.error.ErrorReporter;
import com.google.dart.engine.internal.object.BoolState;
import com.google.dart.engine.internal.object.DartObjectImpl;
import com.google.dart.engine.internal.object.GenericState;
import com.google.dart.engine.internal.object.NullState;
import com.google.dart.engine.internal.object.SymbolState;
import com.google.dart.engine.internal.resolver.TypeProvider;
import com.google.dart.engine.type.InterfaceType;
import com.google.dart.engine.utilities.ast.AstCloner;
import com.google.dart.engine.utilities.collection.DirectedGraph;
import com.google.dart.engine.utilities.dart.ParameterKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * Instances of the class {@code ConstantValueComputer} compute the values of constant variables and
 * constant constructor invocations in one or more compilation units. The expected usage pattern is
 * for the compilation units to be added to this computer using the method
 * {@link #add(CompilationUnit)} and then for the method {@link #computeValues()} to be invoked
 * exactly once. Any use of an instance after invoking the method {@link #computeValues()} will
 * result in unpredictable behavior.
 */
public class ConstantValueComputer {
  /**
   * [AstCloner] that copies the necessary information from the AST to allow const constructor
   * initializers to be evaluated.
   */
  private class InitializerCloner extends AstCloner {

    @Override
    public InstanceCreationExpression visitInstanceCreationExpression(
        InstanceCreationExpression node) {
      InstanceCreationExpression expression = super.visitInstanceCreationExpression(node);
      expression.setEvaluationResult(node.getEvaluationResult());
      return expression;
    }

    @Override
    public SimpleIdentifier visitSimpleIdentifier(SimpleIdentifier node) {
      SimpleIdentifier identifier = super.visitSimpleIdentifier(node);
      identifier.setStaticElement(node.getStaticElement());
      return identifier;
    }

    @Override
    public SuperConstructorInvocation visitSuperConstructorInvocation(
        SuperConstructorInvocation node) {
      SuperConstructorInvocation invocation = super.visitSuperConstructorInvocation(node);
      invocation.setStaticElement(node.getStaticElement());
      return invocation;
    }
  }

  /**
   * Parameter to "fromEnvironment" methods that denotes the default value.
   */
  static private final String DEFAULT_VALUE_PARAM = "defaultValue";

  /**
   * Source of RegExp matching declarable operator names. From sdk/lib/internal/symbol.dart.
   */
  static private final String OPERATOR_RE = "(?:[\\-+*/%&|^]|\\[\\]=?|==|~/?|<[<=]?|>[>=]?|unary-)";

  /**
   * Source of RegExp matching any public identifier. From sdk/lib/internal/symbol.dart.
   */
  static private final String PUBLIC_IDENTIFIER_RE = "(?!" + ConstantValueComputer.RESERVED_WORD_RE
      + "\\b(?!\\$))[a-zA-Z$][\\w$]*";

  /**
   * Source of RegExp matching Dart reserved words. From sdk/lib/internal/symbol.dart.
   */
  static private final String RESERVED_WORD_RE = "(?:assert|break|c(?:a(?:se|tch)|lass|on(?:st|tinue))|d(?:efault|o)|"
      + "e(?:lse|num|xtends)|f(?:alse|inal(?:ly)?|or)|i[fns]|n(?:ew|ull)|"
      + "ret(?:hrow|urn)|s(?:uper|witch)|t(?:h(?:is|row)|r(?:ue|y))|"
      + "v(?:ar|oid)|w(?:hile|ith))";

  /**
   * RegExp that validates a non-empty non-private symbol. From sdk/lib/internal/symbol.dart.
   */
  static private final Pattern PUBLIC_SYMBOL_PATTERN = Pattern.compile("^(?:"
      + ConstantValueComputer.OPERATOR_RE + "$|" + PUBLIC_IDENTIFIER_RE + "(?:=?$|[.](?!$)))+?$");

  /**
   * Determine whether the given string is a valid name for a public symbol (i.e. whether it is
   * allowed for a call to the Symbol constructor).
   */
  static public boolean isValidPublicSymbol(String name) {
    return name.isEmpty() || name.equals("void") || PUBLIC_SYMBOL_PATTERN.matcher(name).matches();
  }

  /**
   * The type provider used to access the known types.
   */
  protected TypeProvider typeProvider;

  /**
   * The object used to find constant variables and constant constructor invocations in the
   * compilation units that were added.
   */
  private ConstantFinder constantFinder = new ConstantFinder();

  /**
   * A graph in which the nodes are the constants, and the edges are from each constant to the other
   * constants that are referenced by it.
   */
  protected DirectedGraph<AstNode> referenceGraph = new DirectedGraph<AstNode>();

  /**
   * A table mapping constant variables to the declarations of those variables.
   */
  private HashMap<VariableElement, VariableDeclaration> variableDeclarationMap;

  /**
   * A table mapping constant constructors to the declarations of those constructors.
   */
  protected HashMap<ConstructorElement, ConstructorDeclaration> constructorDeclarationMap;

  /**
   * A collection of constant constructor invocations.
   */
  private ArrayList<InstanceCreationExpression> constructorInvocations;

  /**
   * The set of variables declared on the command line using '-D'.
   */
  private final DeclaredVariables declaredVariables;

  /**
   * Initialize a newly created constant value computer.
   * 
   * @param typeProvider the type provider used to access known types
   * @param declaredVariables the set of variables declared on the command line using '-D'
   */
  public ConstantValueComputer(TypeProvider typeProvider, DeclaredVariables declaredVariables) {
    this.typeProvider = typeProvider;
    this.declaredVariables = declaredVariables;
  }

  /**
   * Add the constants in the given compilation unit to the list of constants whose value needs to
   * be computed.
   * 
   * @param unit the compilation unit defining the constants to be added
   */
  public void add(CompilationUnit unit) {
    unit.accept(constantFinder);
  }

  /**
   * Compute values for all of the constants in the compilation units that were added.
   */
  public void computeValues() {
    variableDeclarationMap = constantFinder.getVariableMap();
    constructorDeclarationMap = constantFinder.getConstructorMap();
    constructorInvocations = constantFinder.getConstructorInvocations();
    for (Map.Entry<VariableElement, VariableDeclaration> entry : variableDeclarationMap.entrySet()) {
      VariableDeclaration declaration = entry.getValue();
      ReferenceFinder referenceFinder = new ReferenceFinder(
          declaration,
          referenceGraph,
          variableDeclarationMap,
          constructorDeclarationMap);
      referenceGraph.addNode(declaration);
      declaration.getInitializer().accept(referenceFinder);
    }
    for (Entry<ConstructorElement, ConstructorDeclaration> entry : constructorDeclarationMap.entrySet()) {
      ConstructorDeclaration declaration = entry.getValue();
      ReferenceFinder referenceFinder = new ReferenceFinder(
          declaration,
          referenceGraph,
          variableDeclarationMap,
          constructorDeclarationMap);
      referenceGraph.addNode(declaration);
      boolean superInvocationFound = false;
      NodeList<ConstructorInitializer> initializers = declaration.getInitializers();
      for (ConstructorInitializer initializer : initializers) {
        if (initializer instanceof SuperConstructorInvocation) {
          superInvocationFound = true;
        }
        initializer.accept(referenceFinder);
      }
      if (!superInvocationFound) {
        // No explicit superconstructor invocation found, so we need to manually insert
        // a reference to the implicit superconstructor.
        InterfaceType superclass = ((InterfaceType) entry.getKey().getReturnType()).getSuperclass();
        if (superclass != null && !superclass.isObject()) {
          ConstructorElement unnamedConstructor = superclass.getElement().getUnnamedConstructor();
          ConstructorDeclaration superConstructorDeclaration = findConstructorDeclaration(unnamedConstructor);
          if (superConstructorDeclaration != null) {
            referenceGraph.addEdge(declaration, superConstructorDeclaration);
          }
        }
      }
      for (FormalParameter parameter : declaration.getParameters().getParameters()) {
        referenceGraph.addNode(parameter);
        referenceGraph.addEdge(declaration, parameter);
        if (parameter instanceof DefaultFormalParameter) {
          Expression defaultValue = ((DefaultFormalParameter) parameter).getDefaultValue();
          if (defaultValue != null) {
            ReferenceFinder parameterReferenceFinder = new ReferenceFinder(
                parameter,
                referenceGraph,
                variableDeclarationMap,
                constructorDeclarationMap);
            defaultValue.accept(parameterReferenceFinder);
          }
        }
      }
    }
    for (InstanceCreationExpression expression : constructorInvocations) {
      referenceGraph.addNode(expression);
      ConstructorElement constructor = expression.getStaticElement();
      if (constructor == null) {
        continue;
      }
      constructor = followConstantRedirectionChain(constructor);
      ConstructorDeclaration declaration = findConstructorDeclaration(constructor);
      // An instance creation expression depends both on the constructor and the arguments passed
      // to it.
      ReferenceFinder referenceFinder = new ReferenceFinder(
          expression,
          referenceGraph,
          variableDeclarationMap,
          constructorDeclarationMap);
      if (declaration != null) {
        referenceGraph.addEdge(expression, declaration);
      }
      expression.getArgumentList().accept(referenceFinder);
    }
    ArrayList<ArrayList<AstNode>> topologicalSort = referenceGraph.computeTopologicalSort();
    for (ArrayList<AstNode> constantsInCycle : topologicalSort) {
      if (constantsInCycle.size() == 1) {
        computeValueFor(constantsInCycle.get(0));
      } else {
        for (AstNode constant : constantsInCycle) {
          generateCycleError(constantsInCycle, constant);
        }
      }
    }
  }

  /**
   * This method is called just before computing the constant value associated with an AST node.
   * Unit tests will override this method to introduce additional error checking.
   */
  protected void beforeComputeValue(AstNode constNode) {
  }

  /**
   * This method is called just before getting the constant initializers associated with a
   * constructor AST node. Unit tests will override this method to introduce additional error
   * checking.
   */
  protected void beforeGetConstantInitializers(ConstructorElement constructor) {
  }

  /**
   * This method is called just before getting a parameter's default value. Unit tests will override
   * this method to introduce additional error checking.
   */
  protected void beforeGetParameterDefault(ParameterElement parameter) {
  }

  /**
   * Create the ConstantVisitor used to evaluate constants. Unit tests will override this method to
   * introduce additional error checking.
   */
  protected ConstantVisitor createConstantVisitor(ErrorReporter errorReporter) {
    return new ConstantVisitor(typeProvider, errorReporter);
  }

  protected ConstructorDeclaration findConstructorDeclaration(ConstructorElement constructor) {
    return constructorDeclarationMap.get(getConstructorBase(constructor));
  }

  /**
   * Check that the arguments to a call to fromEnvironment() are correct.
   * 
   * @param arguments the AST nodes of the arguments.
   * @param argumentValues the values of the unnamed arguments.
   * @param namedArgumentValues the values of the named arguments.
   * @param expectedDefaultValueType the allowed type of the "defaultValue" parameter (if present).
   *          Note: "defaultValue" is always allowed to be null.
   * @return true if the arguments are correct, false if there is an error.
   */
  private boolean checkFromEnvironmentArguments(NodeList<Expression> arguments,
      DartObjectImpl[] argumentValues, HashMap<String, DartObjectImpl> namedArgumentValues,
      InterfaceType expectedDefaultValueType) {
    int argumentCount = arguments.size();
    if (argumentCount < 1 || argumentCount > 2) {
      return false;
    }
    if (arguments.get(0) instanceof NamedExpression) {
      return false;
    }
    if (argumentValues[0].getType() != typeProvider.getStringType()) {
      return false;
    }
    if (argumentCount == 2) {
      if (!(arguments.get(1) instanceof NamedExpression)) {
        return false;
      }
      if (!(((NamedExpression) arguments.get(1)).getName().getLabel().getName().equals(DEFAULT_VALUE_PARAM))) {
        return false;
      }
      InterfaceType defaultValueType = namedArgumentValues.get(DEFAULT_VALUE_PARAM).getType();
      if (!(defaultValueType == expectedDefaultValueType || defaultValueType == typeProvider.getNullType())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check that the arguments to a call to Symbol() are correct.
   * 
   * @param arguments the AST nodes of the arguments.
   * @param argumentValues the values of the unnamed arguments.
   * @param namedArgumentValues the values of the named arguments.
   * @return true if the arguments are correct, false if there is an error.
   */
  private boolean checkSymbolArguments(NodeList<Expression> arguments,
      DartObjectImpl[] argumentValues, HashMap<String, DartObjectImpl> namedArgumentValues) {
    if (arguments.size() != 1) {
      return false;
    }
    if (arguments.get(0) instanceof NamedExpression) {
      return false;
    }
    if (argumentValues[0].getType() != typeProvider.getStringType()) {
      return false;
    }
    String name = argumentValues[0].getStringValue();
    return isValidPublicSymbol(name);
  }

  /**
   * Compute a value for the given constant.
   * 
   * @param constNode the constant for which a value is to be computed
   */
  private void computeValueFor(AstNode constNode) {
    beforeComputeValue(constNode);
    if (constNode instanceof VariableDeclaration) {
      VariableDeclaration declaration = (VariableDeclaration) constNode;
      Element element = declaration.getElement();
      RecordingErrorListener errorListener = new RecordingErrorListener();
      ErrorReporter errorReporter = new ErrorReporter(errorListener, element.getSource());
      DartObjectImpl dartObject = declaration.getInitializer().accept(
          createConstantVisitor(errorReporter));
      ((VariableElementImpl) element).setEvaluationResult(new EvaluationResultImpl(
          dartObject,
          errorListener.getErrors()));
    } else if (constNode instanceof InstanceCreationExpression) {
      InstanceCreationExpression expression = (InstanceCreationExpression) constNode;
      ConstructorElement constructor = expression.getStaticElement();
      if (constructor == null) {
        // Couldn't resolve the constructor so we can't compute a value.  No problem--the error
        // has already been reported.  But we still need to store an evaluation result.
        expression.setEvaluationResult(new EvaluationResultImpl(null));
        return;
      }
      RecordingErrorListener errorListener = new RecordingErrorListener();
      CompilationUnit sourceCompilationUnit = expression.getAncestor(CompilationUnit.class);
      ErrorReporter errorReporter = new ErrorReporter(
          errorListener,
          sourceCompilationUnit.getElement().getSource());
      ConstantVisitor constantVisitor = createConstantVisitor(errorReporter);
      DartObjectImpl result = evaluateConstructorCall(
          constNode,
          expression.getArgumentList().getArguments(),
          constructor,
          constantVisitor,
          errorReporter);
      expression.setEvaluationResult(new EvaluationResultImpl(result, errorListener.getErrors()));
    } else if (constNode instanceof ConstructorDeclaration) {
      ConstructorDeclaration declaration = (ConstructorDeclaration) constNode;
      NodeList<ConstructorInitializer> initializers = declaration.getInitializers();
      ConstructorElementImpl constructor = (ConstructorElementImpl) declaration.getElement();
      constructor.setConstantInitializers(new InitializerCloner().cloneNodeList(initializers));
    } else if (constNode instanceof FormalParameter) {
      if (constNode instanceof DefaultFormalParameter) {
        DefaultFormalParameter parameter = ((DefaultFormalParameter) constNode);
        ParameterElement element = parameter.getElement();
        Expression defaultValue = parameter.getDefaultValue();
        if (defaultValue != null) {
          RecordingErrorListener errorListener = new RecordingErrorListener();
          ErrorReporter errorReporter = new ErrorReporter(errorListener, element.getSource());
          DartObjectImpl dartObject = defaultValue.accept(createConstantVisitor(errorReporter));
          ((ParameterElementImpl) element).setEvaluationResult(new EvaluationResultImpl(
              dartObject,
              errorListener.getErrors()));
        }
      }
    } else {
      // Should not happen.
      AnalysisEngine.getInstance().getLogger().logError(
          "Constant value computer trying to compute the value of a node which is not a "
              + "VariableDeclaration, InstanceCreationExpression, FormalParameter, or "
              + "ConstructorDeclaration");
      return;
    }
  }

  /**
   * Evaluate a call to fromEnvironment() on the bool, int, or String class.
   * 
   * @param environmentValue Value fetched from the environment
   * @param builtInDefaultValue Value that should be used as the default if no "defaultValue"
   *          argument appears in {@link namedArgumentValues}.
   * @param namedArgumentValues Named parameters passed to fromEnvironment()
   * @return A {@link DartObjectImpl} object corresponding to the evaluated result
   */
  private DartObjectImpl computeValueFromEnvironment(DartObject environmentValue,
      DartObjectImpl builtInDefaultValue, HashMap<String, DartObjectImpl> namedArgumentValues) {
    DartObjectImpl value = (DartObjectImpl) environmentValue;
    if (value.isUnknown() || value.isNull()) {
      // The name either doesn't exist in the environment or we couldn't parse the corresponding
      // value.  If the code supplied an explicit default, use it.
      if (namedArgumentValues.containsKey(DEFAULT_VALUE_PARAM)) {
        value = namedArgumentValues.get(DEFAULT_VALUE_PARAM);
      } else if (value.isNull()) {
        // The code didn't supply an explicit default.  The name exists in the environment but
        // we couldn't parse the corresponding value.  So use the built-in default value, because
        // this is what the VM does.
        value = builtInDefaultValue;
      } else {
        // The code didn't supply an explicit default.  The name doesn't exist in the environment.
        // The VM would use the built-in default value, but we don't want to do that for analysis
        // because it's likely to lead to cascading errors.  So just leave [value] in the unknown
        // state.
      }
    }
    return value;
  }

  private DartObjectImpl evaluateConstructorCall(AstNode node, NodeList<Expression> arguments,
      ConstructorElement constructor, ConstantVisitor constantVisitor, ErrorReporter errorReporter) {
    int argumentCount = arguments.size();
    DartObjectImpl[] argumentValues = new DartObjectImpl[argumentCount];
    HashMap<String, DartObjectImpl> namedArgumentValues = new HashMap<String, DartObjectImpl>();
    for (int i = 0; i < argumentCount; i++) {
      Expression argument = arguments.get(i);
      if (argument instanceof NamedExpression) {
        NamedExpression namedExpression = (NamedExpression) argument;
        String name = namedExpression.getName().getLabel().getName();
        namedArgumentValues.put(name, constantVisitor.valueOf(namedExpression.getExpression()));
        argumentValues[i] = constantVisitor.getNull();
      } else {
        argumentValues[i] = constantVisitor.valueOf(argument);
      }
    }
    constructor = followConstantRedirectionChain(constructor);
    InterfaceType definingClass = (InterfaceType) constructor.getReturnType();
    if (constructor.isFactory()) {
      // We couldn't find a non-factory constructor.  See if it's because we reached an external
      // const factory constructor that we can emulate.
      if (constructor.getName().equals("fromEnvironment")) {
        if (!checkFromEnvironmentArguments(
            arguments,
            argumentValues,
            namedArgumentValues,
            definingClass)) {
          errorReporter.reportErrorForNode(CompileTimeErrorCode.CONST_EVAL_THROWS_EXCEPTION, node);
          return null;
        }
        String variableName = argumentCount < 1 ? null : argumentValues[0].getStringValue();
        if (definingClass == typeProvider.getBoolType()) {
          DartObject valueFromEnvironment;
          valueFromEnvironment = declaredVariables.getBool(typeProvider, variableName);
          return computeValueFromEnvironment(
              valueFromEnvironment,
              new DartObjectImpl(typeProvider.getBoolType(), BoolState.FALSE_STATE),
              namedArgumentValues);
        } else if (definingClass == typeProvider.getIntType()) {
          DartObject valueFromEnvironment;
          valueFromEnvironment = declaredVariables.getInt(typeProvider, variableName);
          return computeValueFromEnvironment(
              valueFromEnvironment,
              new DartObjectImpl(typeProvider.getNullType(), NullState.NULL_STATE),
              namedArgumentValues);
        } else if (definingClass == typeProvider.getStringType()) {
          DartObject valueFromEnvironment;
          valueFromEnvironment = declaredVariables.getString(typeProvider, variableName);
          return computeValueFromEnvironment(
              valueFromEnvironment,
              new DartObjectImpl(typeProvider.getNullType(), NullState.NULL_STATE),
              namedArgumentValues);
        }
      } else if (constructor.getName().equals("") && definingClass == typeProvider.getSymbolType()
          && argumentCount == 1) {
        if (!checkSymbolArguments(arguments, argumentValues, namedArgumentValues)) {
          errorReporter.reportErrorForNode(CompileTimeErrorCode.CONST_EVAL_THROWS_EXCEPTION, node);
          return null;
        }
        String argumentValue = argumentValues[0].getStringValue();
        return new DartObjectImpl(definingClass, new SymbolState(argumentValue));
      }

      // Either it's an external const factory constructor that we can't emulate, or an error
      // occurred (a cycle, or a const constructor trying to delegate to a non-const constructor).
      // In the former case, the best we can do is consider it an unknown value.  In the latter
      // case, the error has already been reported, so considering it an unknown value will
      // suppress further errors.
      return constantVisitor.validWithUnknownValue(definingClass);
    }
    beforeGetConstantInitializers(constructor);
    ConstructorElementImpl constructorBase = (ConstructorElementImpl) getConstructorBase(constructor);
    List<ConstructorInitializer> initializers = constructorBase.getConstantInitializers();
    if (initializers == null) {
      // This can happen in some cases where there are compile errors in the code being analyzed
      // (for example if the code is trying to create a const instance using a non-const
      // constructor, or the node we're visiting is involved in a cycle).  The error has already
      // been reported, so consider it an unknown value to suppress further errors.
      return constantVisitor.validWithUnknownValue(definingClass);
    }
    HashMap<String, DartObjectImpl> fieldMap = new HashMap<String, DartObjectImpl>();
    HashMap<String, DartObjectImpl> parameterMap = new HashMap<String, DartObjectImpl>();
    ParameterElement[] parameters = constructorBase.getParameters();
    int parameterCount = parameters.length;
    for (int i = 0; i < parameterCount; i++) {
      ParameterElement parameter = parameters[i];
      while (parameter instanceof ParameterMember) {
        parameter = ((ParameterMember) parameter).getBaseElement();
      }
      DartObjectImpl argumentValue = null;
      if (parameter.getParameterKind() == ParameterKind.NAMED) {
        argumentValue = namedArgumentValues.get(parameter.getName());
      } else if (i < argumentCount) {
        argumentValue = argumentValues[i];
      }
      if (argumentValue == null && parameter instanceof ParameterElementImpl) {
        // The parameter is an optional positional parameter for which no value was provided, so
        // use the default value.
        beforeGetParameterDefault(parameter);
        EvaluationResultImpl evaluationResult = ((ParameterElementImpl) parameter).getEvaluationResult();
        if (evaluationResult == null) {
          // No default was provided, so the default value is null.
          argumentValue = constantVisitor.getNull();
        } else if (evaluationResult.getValue() != null) {
          argumentValue = evaluationResult.getValue();
        }
      }
      if (argumentValue != null) {
        if (parameter.isInitializingFormal()) {
          FieldElement field = ((FieldFormalParameterElement) parameter).getField();
          if (field != null) {
            String fieldName = field.getName();
            fieldMap.put(fieldName, argumentValue);
          }
        } else {
          String name = parameter.getName();
          parameterMap.put(name, argumentValue);
        }
      }
    }
    ConstantVisitor initializerVisitor = new ConstantVisitor(
        typeProvider,
        parameterMap,
        errorReporter);
    String superName = null;
    NodeList<Expression> superArguments = null;
    for (ConstructorInitializer initializer : initializers) {
      if (initializer instanceof ConstructorFieldInitializer) {
        ConstructorFieldInitializer constructorFieldInitializer = (ConstructorFieldInitializer) initializer;
        Expression initializerExpression = constructorFieldInitializer.getExpression();
        DartObjectImpl evaluationResult = initializerExpression.accept(initializerVisitor);
        if (evaluationResult != null) {
          String fieldName = constructorFieldInitializer.getFieldName().getName();
          fieldMap.put(fieldName, evaluationResult);
        }
      } else if (initializer instanceof SuperConstructorInvocation) {
        SuperConstructorInvocation superConstructorInvocation = (SuperConstructorInvocation) initializer;
        SimpleIdentifier name = superConstructorInvocation.getConstructorName();
        if (name != null) {
          superName = name.getName();
        }
        superArguments = superConstructorInvocation.getArgumentList().getArguments();
      }
    }
    // Evaluate explicit or implicit call to super().
    InterfaceType superclass = definingClass.getSuperclass();
    if (superclass != null && !superclass.isObject()) {
      ConstructorElement superConstructor = superclass.lookUpConstructor(
          superName,
          constructor.getLibrary());
      if (superConstructor != null) {
        if (superArguments == null) {
          superArguments = new NodeList<Expression>(null);
        }
        evaluateSuperConstructorCall(
            node,
            fieldMap,
            superConstructor,
            superArguments,
            initializerVisitor,
            errorReporter);
      }
    }
    return new DartObjectImpl(definingClass, new GenericState(fieldMap));
  }

  private void evaluateSuperConstructorCall(AstNode node, HashMap<String, DartObjectImpl> fieldMap,
      ConstructorElement superConstructor, NodeList<Expression> superArguments,
      ConstantVisitor initializerVisitor, ErrorReporter errorReporter) {
    if (superConstructor != null && superConstructor.isConst()) {
      DartObjectImpl evaluationResult = evaluateConstructorCall(
          node,
          superArguments,
          superConstructor,
          initializerVisitor,
          errorReporter);
      if (evaluationResult != null) {
        fieldMap.put(GenericState.SUPERCLASS_FIELD, evaluationResult);
      }
    }
  }

  /**
   * Attempt to follow the chain of factory redirections until a constructor is reached which is not
   * a const factory constructor.
   * 
   * @return the constant constructor which terminates the chain of factory redirections, if the
   *         chain terminates. If there is a problem (e.g. a redirection can't be found, or a cycle
   *         is encountered), the chain will be followed as far as possible and then a const factory
   *         constructor will be returned.
   */
  private ConstructorElement followConstantRedirectionChain(ConstructorElement constructor) {
    HashSet<ConstructorElement> constructorsVisited = new HashSet<ConstructorElement>();
    while (constructor.isFactory()) {
      if (constructor.getEnclosingElement().getType() == typeProvider.getSymbolType()) {
        // The dart:core.Symbol has a const factory constructor that redirects to
        // dart:_internal.Symbol.  That in turn redirects to an external const constructor, which
        // we won't be able to evaluate.  So stop following the chain of redirections at
        // dart:core.Symbol, and let [evaluateInstanceCreationExpression] handle it specially.
        break;
      }

      constructorsVisited.add(constructor);
      ConstructorElement redirectedConstructor = constructor.getRedirectedConstructor();
      if (redirectedConstructor == null) {
        // This can happen if constructor is an external factory constructor.
        break;
      }
      if (!redirectedConstructor.isConst()) {
        // Delegating to a non-const constructor--this is not allowed (and
        // is checked elsewhere--see [ErrorVerifier.checkForRedirectToNonConstConstructor()]).
        break;
      }
      if (constructorsVisited.contains(redirectedConstructor)) {
        // Cycle in redirecting factory constructors--this is not allowed
        // and is checked elsewhere--see [ErrorVerifier.checkForRecursiveFactoryRedirect()]).
        break;
      }
      constructor = redirectedConstructor;
    }
    return constructor;
  }

  /**
   * Generate an error indicating that the given constant is not a valid compile-time constant
   * because it references at least one of the constants in the given cycle, each of which directly
   * or indirectly references the constant.
   * 
   * @param constantsInCycle the constants in the cycle that includes the given constant
   * @param constant the constant that is not a valid compile-time constant
   */
  private void generateCycleError(List<AstNode> constantsInCycle, AstNode constant) {
    // TODO(brianwilkerson) Implement this.
  }

  private ConstructorElement getConstructorBase(ConstructorElement constructor) {
    while (constructor instanceof ConstructorMember) {
      constructor = ((ConstructorMember) constructor).getBaseElement();
    }
    return constructor;
  }
}
