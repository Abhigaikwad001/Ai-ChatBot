import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatbotWidgetComponent } from './chatbot-widget/chatbot-widget.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ChatbotWidgetComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  title = 'AI Chatbot Angular Widget';
}
