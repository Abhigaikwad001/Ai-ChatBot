import { Pipe, PipeTransform, inject, SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { marked } from 'marked';

@Pipe({
  name: 'markdown',
  standalone: true,
})
export class MarkdownPipe implements PipeTransform {
  private readonly sanitizer = inject(DomSanitizer);

  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    try {
      const rawHtml = marked.parse(value, {
        async: false,
        breaks: true,
        gfm: true,
      }) as string;
      return this.sanitizer.sanitize(SecurityContext.HTML, rawHtml) || '';
    } catch {
      return '';
    }
  }
}

