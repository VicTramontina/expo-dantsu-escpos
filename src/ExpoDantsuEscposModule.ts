import { requireNativeModule } from "expo-modules-core";

import type { ExpoDantsuEscposModule as ExpoDantsuEscposModuleType } from "./ExpoDantsuEscpos.types";

const nativeModule = requireNativeModule<ExpoDantsuEscposModuleType>(
  "ExpoDantsuEscposModule",
);

export default nativeModule;

