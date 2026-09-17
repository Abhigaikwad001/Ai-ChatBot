import { TestBed } from '@angular/core/testing';
import { DomSanitizer } from '@angular/platform-browser';
import { marked } from 'marked';
import { MarkdownPipe } from './markdown.pipe';

describe('MarkdownPipe', () => {
  let pipe: MarkdownPipe;
  let sanitizer: DomSanitizer;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MarkdownPipe],
    });
    sanitizer = TestBed.inject(DomSanitizer);
    pipe = TestBed.inject(MarkdownPipe);
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  it('should return empty string for null or empty input', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform('')).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });

  it('should correctly format bold, italic, and paragraphs', () => {
    const output = pipe.transform('**Bold text** and *italic text*');
    expect(output).toContain('<strong>Bold text</strong>');
    expect(output).toContain('<em>italic text</em>');
  });

  it('should correctly format lists and code blocks', () => {
    const markdown = '- Bullet 1\n- Bullet 2\n\n```javascript\nconst x = 10;\n```';
    const output = pipe.transform(markdown);
    expect(output).toContain('<ul>');
    expect(output).toContain('<li>Bullet 1</li>');
    expect(output).toContain('<pre><code');
    expect(output).toContain('const x = 10;');
  });

  it('should strip dangerous script tags and event handlers to prevent XSS', () => {
    const malicious = '<script>console.log("xss")</script><img src="invalid" onerror="console.log(1)" />Normal text';
    const output = pipe.transform(malicious);
    expect(output).not.toContain('<script>');
    expect(output).not.toContain('onerror');
    expect(output).toContain('Normal text');
  });

  it('should sanitize javascript: URLs in markdown links', () => {
    const malicious = '[Click me](javascript:alert("xss"))';
    const output = pipe.transform(malicious);
    expect(output).not.toContain('href="javascript:');
    expect(output).toMatch(/href="unsafe:javascript:|Click me/);
  });

  it('should sanitize javascript: URLs in raw HTML anchors', () => {
    const malicious = '<a href="javascript:alert(1)">Malicious Anchor</a>';
    const output = pipe.transform(malicious);
    expect(output).not.toContain('href="javascript:');
  });

  it('should neutralize inline event handlers like onload, onclick, onmouseover', () => {
    const malicious = '<svg onload="alert(1)"></svg><div onclick="alert(2)">Click</div><p onmouseover="alert(3)">Hover</p>';
    const output = pipe.transform(malicious);
    expect(output).not.toContain('onload');
    expect(output).not.toContain('onclick');
    expect(output).not.toContain('onmouseover');
  });

  it('should remove unsafe embedded content such as iframe, object, and embed', () => {
    const malicious = '<iframe src="https://attacker.com"></iframe><object data="test"></object><embed src="test">';
    const output = pipe.transform(malicious);
    expect(output).not.toContain('<iframe');
    expect(output).not.toContain('<object');
    expect(output).not.toContain('<embed');
  });

  it('should safely render valid markdown links and formatting', () => {
    const valid = '[Safe Link](https://example.com) and `code`';
    const output = pipe.transform(valid);
    expect(output).toContain('href="https://example.com"');
    expect(output).toContain('Safe Link');
    expect(output).toContain('<code>code</code>');
  });

  it('should return empty string when markdown parsing encounters an error', () => {
    spyOn(marked, 'parse').and.throwError('Parse error');
    expect(pipe.transform('broken input')).toBe('');
  });
});

