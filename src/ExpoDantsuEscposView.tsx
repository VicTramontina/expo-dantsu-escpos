import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoDantsuEscposViewProps } from './ExpoDantsuEscpos.types';

const NativeView: React.ComponentType<ExpoDantsuEscposViewProps> =
  requireNativeView('ExpoDantsuEscpos');

export default function ExpoDantsuEscposView(props: ExpoDantsuEscposViewProps) {
  return <NativeView {...props} />;
}
