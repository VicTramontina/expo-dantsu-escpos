import ExpoDantsuEscposModule from './ExpoDantsuEscposModule';
import type { EscPosTextBuilder } from './ExpoDantsuEscpos.types';

/**
 * ESC/POS Text Builder - A fluent interface for building ESC/POS formatted text
 * following the DantSu ESCPOS-ThermalPrinter-Android library patterns
 */
export class EscPosBuilder implements EscPosTextBuilder {
  private content: string = '';

  /**
   * Add plain text
   */
  text(content: string): EscPosTextBuilder {
    this.content += content;
    return this;
  }

  /**
   * Add center-aligned text
   */
  center(content: string): EscPosTextBuilder {
    this.content += `[C]${content}`;
    return this;
  }

  /**
   * Add left-aligned text
   */
  left(content: string): EscPosTextBuilder {
    this.content += `[L]${content}`;
    return this;
  }

  /**
   * Add right-aligned text
   */
  right(content: string): EscPosTextBuilder {
    this.content += `[R]${content}`;
    return this;
  }

  /**
   * Add bold text
   */
  bold(content: string): EscPosTextBuilder {
    this.content += `<b>${content}</b>`;
    return this;
  }

  /**
   * Add underlined text
   */
  underline(content: string): EscPosTextBuilder {
    this.content += `<u>${content}</u>`;
    return this;
  }

  /**
   * Add big font text
   */
  fontBig(content: string): EscPosTextBuilder {
    this.content += `<font size='big'>${content}</font>`;
    return this;
  }

  /**
   * Add tall font text
   */
  fontTall(content: string): EscPosTextBuilder {
    this.content += `<font size='tall'>${content}</font>`;
    return this;
  }

  /**
   * Add image from base64 string
   */
  image(base64: string, align: 'L' | 'C' | 'R' = 'C', gradient: boolean = false): EscPosTextBuilder {
    // We'll build the image tag manually since we can't call async methods in a builder pattern
    const alignTag = `[${align}]`;
    this.content += `${alignTag}<img data='${base64}' gradient='${gradient}'></img>`;
    return this;
  }

  /**
   * Add barcode
   */
  barcode(data: string, options?: {
    type?: string;
    width?: number;
    height?: number;
    textPosition?: string;
    align?: 'L' | 'C' | 'R';
  }): EscPosTextBuilder {
    const align = options?.align || 'L';
    const alignTag = `[${align}]`;
    
    let tag = `${alignTag}<barcode`;
    if (options?.type) tag += ` type='${options.type}'`;
    if (options?.width) tag += ` width='${options.width}'`;
    if (options?.height) tag += ` height='${options.height}'`;
    if (options?.textPosition) tag += ` text='${options.textPosition}'`;
    tag += `>${data}</barcode>`;
    
    this.content += tag;
    return this;
  }

  /**
   * Add QR code
   */
  qrcode(data: string, options?: {
    size?: number;
    align?: 'L' | 'C' | 'R';
  }): EscPosTextBuilder {
    const align = options?.align || 'C';
    const size = options?.size || 20;
    const alignTag = `[${align}]`;
    
    this.content += `${alignTag}<qrcode size='${size}'>${data}</qrcode>`;
    return this;
  }

  /**
   * Add a new line
   */
  newLine(): EscPosTextBuilder {
    this.content += '\n';
    return this;
  }

  /**
   * Build the final ESC/POS formatted string
   */
  build(): string {
    return this.content;
  }

  /**
   * Process any base64 images in the content and return the final formatted string
   */
  async buildWithImages(): Promise<string> {
    let processedContent = this.content;
    
    // Find all image tags with base64 data
    const imageRegex = /<img data='([^']+)' gradient='([^']+)'><\/img>/g;
    const matches = Array.from(processedContent.matchAll(imageRegex));
    
    for (const match of matches) {
      const [fullMatch, base64, gradientStr] = match;
      const gradient = gradientStr === 'true';
      
      try {
        // Extract alignment from the tag before the img
        const beforeImg = processedContent.substring(0, processedContent.indexOf(fullMatch));
        const alignMatch = beforeImg.match(/\[([LCR])\](?!.*\[([LCR])\])/);
        const align = alignMatch ? alignMatch[1] : 'C';
        
        // Convert the image
        const imgTag = await ExpoDantsuEscposModule.convertImageToEscPos(base64, align, gradient);
        
        // Replace the placeholder with the actual image tag
        processedContent = processedContent.replace(fullMatch, imgTag);
      } catch (error) {
        console.warn('Failed to convert image:', error);
        // Remove the invalid image tag
        processedContent = processedContent.replace(`[${alignMatch?.[1] || 'C'}]${fullMatch}`, '');
      }
    }
    
    return processedContent;
  }
}

/**
 * Create a new ESC/POS text builder
 */
export function createEscPosBuilder(): EscPosTextBuilder {
  return new EscPosBuilder();
}

/**
 * Utility functions for common ESC/POS operations
 */
export const EscPosUtils = {
  /**
   * Create a simple receipt header
   */
  receiptHeader(storeName: string, address?: string): string {
    let header = createEscPosBuilder()
      .center(storeName)
      .newLine();
    
    if (address) {
      header = header.center(address).newLine();
    }
    
    return header.center('------------------------').newLine().build();
  },

  /**
   * Create a receipt footer
   */
  receiptFooter(message?: string): string {
    let footer = createEscPosBuilder()
      .center('------------------------')
      .newLine();
    
    if (message) {
      footer = footer.center(message).newLine();
    }
    
    return footer.center('Thank you!').newLine().newLine().build();
  },

  /**
   * Create a line item for receipts
   */
  lineItem(name: string, price: string, quantity?: number): string {
    const qtyStr = quantity ? ` x${quantity}` : '';
    return createEscPosBuilder()
      .left(`${name}${qtyStr}`)
      .right(price)
      .newLine()
      .build();
  },

  /**
   * Create a separator line
   */
  separator(char: string = '-', length: number = 32): string {
    return createEscPosBuilder()
      .center(char.repeat(length))
      .newLine()
      .build();
  }
};