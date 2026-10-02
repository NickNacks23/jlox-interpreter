package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;

  private final Stack<Map<String, Variable>> scopes = new Stack<>();

  private FunctionType currentFunction = FunctionType.NONE;

  private enum FunctionType {
  NONE,
  FUNCTION,
  INITIALIZER,
  METHOD
}


private enum ClassType {
  NONE,
  CLASS,
  SUBCLASS,
  TRAIT
}

private ClassType currentClass = ClassType.NONE;

  private enum VariableState {
    DECLARED,
    DEFINED,
    READ
  }

  private static class Variable {
    final Token name;
    final int slot;
    VariableState state;

    private Variable(Token name, VariableState state, int slot) {
      this.name = name;
      this.state = state;
      this.slot = slot;
    }
  }

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  private void resolve(Stmt stmt) {
    if (stmt != null) stmt.accept(this);
  }

  private void resolve(Expr expr) {
    if (expr != null) expr.accept(this);
  }

  private void beginScope() {
    scopes.push(new HashMap<String, Variable>());
  }

  private void endScope() {
    Map<String, Variable> scope = scopes.pop();

    for (Map.Entry<String, Variable> entry : scope.entrySet()) {
      if (entry.getValue().state == VariableState.DEFINED) {
        Lox.error(entry.getValue().name,
            "Local variable is not used.");
      }
    }
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, Variable> scope = scopes.peek();

    if (scope.containsKey(name.lexeme)) {
      Lox.error(name,
          "Already a variable with this name in this scope.");
    }

    int slot = scope.size();

    scope.put(
        name.lexeme,
        new Variable(name, VariableState.DECLARED, slot));
  }

  private void define(Token name) {
    if (scopes.isEmpty()) return;

    scopes.peek().get(name.lexeme).state =
        VariableState.DEFINED;
  }

  private void resolveLocal(
      Expr expr, Token name, boolean isRead) {

    for (int i = scopes.size() - 1; i >= 0; i--) {
      Map<String, Variable> scope = scopes.get(i);

      if (scope.containsKey(name.lexeme)) {
        Variable variable = scope.get(name.lexeme);

        interpreter.resolve(
            expr,
            scopes.size() - 1 - i,
            variable.slot);

        if (isRead) {
          variable.state = VariableState.READ;
        }

        return;
      }
    }
  }

  private void resolveFunction(
      Expr.Function function, FunctionType type) {

    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    beginScope();

    if (function.parameters != null) {
  for (Token param : function.parameters) {
    declare(param);
    define(param);
  }
}

    resolve(function.body);

    endScope();

    currentFunction = enclosingFunction;
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);

    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }

    define(stmt.name);
    return null;
  }

  @Override
public Void visitClassStmt(Stmt.Class stmt) {
  ClassType enclosingClass = currentClass;
  currentClass = ClassType.CLASS;

  declare(stmt.name);
  define(stmt.name);

  if (stmt.superclass != null &&
      stmt.name.lexeme.equals(
          ((Expr.Variable)stmt.superclass).name.lexeme)) {
    Lox.error(
        ((Expr.Variable)stmt.superclass).name,
        "A class can't inherit from itself.");
  }

  // Resolve superclass.
  if (stmt.superclass != null) {
    currentClass = ClassType.SUBCLASS;
    resolve(stmt.superclass);

    // Implicit "super" scope.
    beginScope();

    int superSlot = scopes.peek().size();

    scopes.peek().put(
        "super",
        new Variable(
            ((Expr.Variable)stmt.superclass).name,
            VariableState.READ,
            superSlot));
  }

  // Resolve applied traits.
  for (Expr trait : stmt.traits) {
    resolve(trait);
  }

  // Implicit "this" scope.
  beginScope();

  int thisSlot = scopes.peek().size();

  scopes.peek().put(
      "this",
      new Variable(
          stmt.name,
          VariableState.READ,
          thisSlot));

          int innerSlot = scopes.peek().size();

scopes.peek().put(
    "inner",
    new Variable(
        stmt.name,
        VariableState.READ,
        innerSlot));

  // Normal instance methods.
  for (Stmt.Function method : stmt.methods) {
    FunctionType declaration =
        FunctionType.METHOD;

    if (method.name.lexeme.equals("init")) {
      declaration =
          FunctionType.INITIALIZER;
    }

    resolveFunction(
        method.function,
        declaration);
  }

  // Preserve Chapter 12 class/static methods.
  for (Stmt.Function method : stmt.classMethods) {
    resolveFunction(
        method.function,
        FunctionType.METHOD);
  }

  endScope();

  if (stmt.superclass != null) {
    endScope();
  }

  currentClass = enclosingClass;

  return null;
}

@Override
public Void visitGetExpr(Expr.Get expr) {
  resolve(expr.object);
  return null;
}




@Override
public Void visitTraitStmt(Stmt.Trait stmt) {
  declare(stmt.name);
  define(stmt.name);

  ClassType enclosingClass = currentClass;
  currentClass = ClassType.TRAIT;

  // A trait can compose other traits.
  for (Expr trait : stmt.traits) {
    resolve(trait);
  }

  // Trait methods can use "this".
  beginScope();

  int thisSlot = scopes.peek().size();

  scopes.peek().put(
      "this",
      new Variable(
          stmt.name,
          VariableState.READ,
          thisSlot));

  for (Stmt.Function method : stmt.methods) {
    resolveFunction(
        method.function,
        FunctionType.METHOD);
  }

  endScope();

  currentClass = enclosingClass;

  return null;
}


@Override
public Void visitSuperExpr(Expr.Super expr) {
  if (currentClass == ClassType.NONE) {
    Lox.error(
        expr.keyword,
        "Can't use 'super' outside of a class.");
  } else if (currentClass == ClassType.TRAIT) {
    Lox.error(
        expr.keyword,
        "Can't use 'super' in a trait.");
  } else if (currentClass != ClassType.SUBCLASS) {
    Lox.error(
        expr.keyword,
        "Can't use 'super' in a class with no superclass.");
  }

  resolveLocal(
      expr,
      expr.keyword,
      true);

  return null;
}



@Override
public Void visitSetExpr(Expr.Set expr) {
  resolve(expr.value);
  resolve(expr.object);
  return null;
}


@Override
public Void visitThisExpr(Expr.This expr) {
  if (currentClass == ClassType.NONE) {
    Lox.error(
        expr.keyword,
        "Can't use 'this' outside of a class.");
    return null;
  }

  resolveLocal(expr, expr.keyword, true);
  return null;
}


  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
    if (!scopes.isEmpty()
        && scopes.peek().containsKey(expr.name.lexeme)
        && scopes.peek().get(expr.name.lexeme).state
            == VariableState.DECLARED) {

      Lox.error(expr.name,
          "Can't read local variable in its own initializer.");
    }

    resolveLocal(expr, expr.name, true);
    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
    resolveLocal(expr, expr.name, false);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);

    resolveFunction(
        stmt.function, FunctionType.FUNCTION);

    return null;
  }

  @Override
  public Void visitFunctionExpr(Expr.Function expr) {
    resolveFunction(expr, FunctionType.FUNCTION);
    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);

    if (stmt.elseBranch != null) {
      resolve(stmt.elseBranch);
    }

    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    if (currentFunction == FunctionType.NONE) {
      Lox.error(
          stmt.keyword,
          "Can't return from top-level code.");
    }

    if (stmt.value != null) {
  if (currentFunction == FunctionType.INITIALIZER) {
    Lox.error(
        stmt.keyword,
        "Can't return a value from an initializer.");
  }

  resolve(stmt.value);
}

    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.body);
    return null;
  }

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    return null;
  }

  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);
    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);
    return null;
  }
}