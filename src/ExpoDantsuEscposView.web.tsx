import * as React from 'react';

import { ExpoDantsuEscposViewProps } from './ExpoDantsuEscpos.types';

export default function ExpoDantsuEscposView(props: ExpoDantsuEscposViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
