package proof.reset;

import io.papermc.paper.ServerBuildInfo;
import java.util.OptionalInt;

final class RuntimeIdentity {
    private RuntimeIdentity() {
    }

    static String describe() {
        ServerBuildInfo info = ServerBuildInfo.buildInfo();
        return "RUNTIME|brandId=" + info.brandId()
                + "|minecraft=" + info.minecraftVersionId()
                + "|build=" + optionalInt(info.buildNumber());
    }

    private static String optionalInt(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "missing";
    }
}
