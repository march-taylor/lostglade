public class Probe {
  public static void main(String[] args) throws Exception {
    var methods = net.minecraft.server.players.UserBanList.class.getMethods();
    java.util.Arrays.stream(methods)
      .map(java.lang.reflect.Method::getName)
      .distinct()
      .sorted()
      .forEach(System.out::println);
  }
}
