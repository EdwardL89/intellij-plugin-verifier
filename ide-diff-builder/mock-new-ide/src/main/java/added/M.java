package added;

import java.util.Map;

public class M {

  public int f1;
  public String f2;
  //Private stuff should not be reported as added
  private int f3;

  public M(int x) {
  }

  public static void m2() {
  }

  public void m3(Map<Integer, Integer> m) {
  }

  public void m1() {
  }

  private void m3() {
  }

  public static class C {
  }

  public class B {
    public class D {
    }
  }
}