import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatbotWidgetComponent } from './chatbot-widget.component';

describe('ChatbotWidgetComponent', () => {
  let component: ChatbotWidgetComponent;
  let fixture: ComponentFixture<ChatbotWidgetComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatbotWidgetComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatbotWidgetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the chatbot widget component', () => {
    expect(component).toBeTruthy();
  });

  it('should initially be closed with floating launcher button visible', () => {
    expect(component.isOpen).toBeFalse();

    const compiled = fixture.nativeElement as HTMLElement;
    const button = compiled.querySelector('.floating-launcher-btn');
    expect(button).toBeTruthy();
    expect(button?.getAttribute('aria-expanded')).toBe('false');

    const panel = compiled.querySelector('.widget-panel');
    expect(panel).toBeFalsy();
  });

  it('should toggle open state when clicking the floating launcher button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const button = compiled.querySelector('.floating-launcher-btn') as HTMLButtonElement;

    button.click();
    fixture.detectChanges();

    expect(component.isOpen).toBeTrue();
    expect(compiled.querySelector('.widget-panel')).toBeTruthy();
    expect(button.getAttribute('aria-expanded')).toBe('true');

    button.click();
    fixture.detectChanges();

    expect(component.isOpen).toBeFalse();
    expect(compiled.querySelector('.widget-panel')).toBeFalsy();
  });

  it('should close the panel when Escape key is pressed', () => {
    component.openChat();
    fixture.detectChanges();
    expect(component.isOpen).toBeTrue();

    const escapeEvent = new KeyboardEvent('keydown', { key: 'Escape' });
    document.dispatchEvent(escapeEvent);
    fixture.detectChanges();

    expect(component.isOpen).toBeFalse();
  });
});
