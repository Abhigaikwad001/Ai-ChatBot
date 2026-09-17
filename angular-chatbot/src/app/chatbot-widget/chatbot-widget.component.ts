import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatbotPanelComponent } from '../chatbot-panel/chatbot-panel.component';

@Component({
  selector: 'app-chatbot-widget',
  standalone: true,
  imports: [CommonModule, ChatbotPanelComponent],
  templateUrl: './chatbot-widget.component.html',
  styleUrls: ['./chatbot-widget.component.scss'],
})
export class ChatbotWidgetComponent {
  public isOpen = false;

  public toggleChat(): void {
    this.isOpen = !this.isOpen;
  }

  public openChat(): void {
    this.isOpen = true;
  }

  public closeChat(): void {
    this.isOpen = false;
  }

  @HostListener('document:keydown.escape')
  public onEscapePressed(): void {
    if (this.isOpen) {
      this.closeChat();
    }
  }
}
