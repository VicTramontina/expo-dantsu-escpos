import { registerWebModule, NativeModule } from 'expo';

import { ExpoDantsuEscposModuleEvents } from './ExpoDantsuEscpos.types';

class ExpoDantsuEscposModule extends NativeModule<ExpoDantsuEscposModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(ExpoDantsuEscposModule, 'ExpoDantsuEscposModule');
