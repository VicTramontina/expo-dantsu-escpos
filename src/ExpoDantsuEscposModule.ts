import { NativeModule, requireNativeModule } from 'expo';

import { ExpoDantsuEscposModuleEvents } from './ExpoDantsuEscpos.types';

declare class ExpoDantsuEscposModule extends NativeModule<ExpoDantsuEscposModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoDantsuEscposModule>('ExpoDantsuEscpos');
