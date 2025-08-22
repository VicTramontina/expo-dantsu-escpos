import { NativeModule, requireNativeModule } from "expo";
declare class ExpoDantsuEscposModule extends NativeModule {
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoDantsuEscposModule>(
  "ExpoDantsuEscposModule",
);
