"""
⚡ BitcoinMesh Client - Android App
Broadcast Bitcoin transactions over LoRa mesh network
Created by Silexperience & ProfEduStream
"""

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.scrollview import ScrollView
from kivy.uix.spinner import Spinner
from kivy.uix.popup import Popup
from kivy.graphics import Color, Rectangle, RoundedRectangle, Line
from kivy.clock import Clock
from kivy.core.clipboard import Clipboard
from kivy.utils import get_color_from_hex
from kivy.animation import Animation
from kivy.metrics import dp
from kivy.core.window import Window

# Dark theme colors
BG_DARK = get_color_from_hex('#0a0a0f')
BG_CARD = get_color_from_hex('#12121a')
BG_INPUT = get_color_from_hex('#1a1a25')
ORANGE_NEON = get_color_from_hex('#ff6b00')
ORANGE_GLOW = get_color_from_hex('#ff8c00')
ORANGE_LIGHT = get_color_from_hex('#ffaa00')
TEXT_WHITE = get_color_from_hex('#ffffff')
TEXT_GRAY = get_color_from_hex('#888899')
GREEN_SUCCESS = get_color_from_hex('#00ff88')
RED_ERROR = get_color_from_hex('#ff4444')

# Chunk size for Meshtastic messages
CHUNK_SIZE = 190  # Leave room for header

class NeonButton(Button):
    """Button with orange neon glow effect"""
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.background_normal = ''
        self.background_color = (0, 0, 0, 0)
        self.color = ORANGE_NEON
        self.font_size = dp(16)
        self.bold = True
        self.bind(pos=self.update_canvas, size=self.update_canvas)
        self.bind(state=self.on_state_change)
        
    def update_canvas(self, *args):
        self.canvas.before.clear()
        with self.canvas.before:
            # Glow effect
            Color(*ORANGE_NEON[:3], 0.3)
            RoundedRectangle(pos=(self.x - dp(3), self.y - dp(3)), 
                           size=(self.width + dp(6), self.height + dp(6)), 
                           radius=[dp(12)])
            # Main button
            Color(*BG_CARD)
            RoundedRectangle(pos=self.pos, size=self.size, radius=[dp(10)])
            # Border
            Color(*ORANGE_NEON)
            Line(rounded_rectangle=(self.x, self.y, self.width, self.height, dp(10)), width=dp(1.5))
    
    def on_state_change(self, instance, value):
        if value == 'down':
            self.color = ORANGE_LIGHT
        else:
            self.color = ORANGE_NEON


class GlowLabel(Label):
    """Label with subtle glow effect"""
    def __init__(self, glow=False, **kwargs):
        super().__init__(**kwargs)
        self.color = TEXT_WHITE
        self.glow = glow
        if glow:
            self.bind(pos=self.update_glow, size=self.update_glow)
    
    def update_glow(self, *args):
        if self.glow:
            self.outline_color = ORANGE_NEON
            self.outline_width = dp(1)


class NeonTextInput(TextInput):
    """Text input with neon border"""
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.background_normal = ''
        self.background_active = ''
        self.background_color = BG_INPUT
        self.foreground_color = TEXT_WHITE
        self.cursor_color = ORANGE_NEON
        self.hint_text_color = TEXT_GRAY
        self.font_size = dp(14)
        self.padding = [dp(15), dp(15)]
        self.bind(pos=self.update_border, size=self.update_border, focus=self.update_border)
        
    def update_border(self, *args):
        self.canvas.after.clear()
        with self.canvas.after:
            if self.focus:
                Color(*ORANGE_NEON)
                Line(rounded_rectangle=(self.x, self.y, self.width, self.height, dp(8)), width=dp(2))
            else:
                Color(*TEXT_GRAY[:3], 0.3)
                Line(rounded_rectangle=(self.x, self.y, self.width, self.height, dp(8)), width=dp(1))


class ChunkDisplay(BoxLayout):
    """Display a single chunk with copy button"""
    def __init__(self, chunk_num, total, data, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'horizontal'
        self.size_hint_y = None
        self.height = dp(60)
        self.padding = [dp(10), dp(5)]
        self.spacing = dp(10)
        self.data = data
        
        # Background
        with self.canvas.before:
            Color(*BG_CARD)
            self.rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[dp(8)])
        self.bind(pos=self.update_rect, size=self.update_rect)
        
        # Chunk info
        info_layout = BoxLayout(orientation='vertical', size_hint_x=0.7)
        
        header = Label(
            text=f'[color=ff6b00]⚡ Partie {chunk_num}/{total}[/color]',
            markup=True,
            font_size=dp(14),
            halign='left',
            size_hint_y=0.5
        )
        header.bind(size=header.setter('text_size'))
        
        preview = Label(
            text=f'[color=888899]{data[:40]}...[/color]',
            markup=True,
            font_size=dp(11),
            halign='left',
            size_hint_y=0.5
        )
        preview.bind(size=preview.setter('text_size'))
        
        info_layout.add_widget(header)
        info_layout.add_widget(preview)
        
        # Copy button
        copy_btn = NeonButton(text='📋 COPIER', size_hint_x=0.3)
        copy_btn.bind(on_press=self.copy_chunk)
        
        self.add_widget(info_layout)
        self.add_widget(copy_btn)
    
    def update_rect(self, *args):
        self.rect.pos = self.pos
        self.rect.size = self.size
    
    def copy_chunk(self, instance):
        Clipboard.copy(self.data)
        instance.text = '✅ COPIÉ!'
        Clock.schedule_once(lambda dt: setattr(instance, 'text', '📋 COPIER'), 1.5)


class BitcoinMeshApp(App):
    def build(self):
        Window.clearcolor = BG_DARK
        
        # Main layout
        self.root = FloatLayout()
        
        # Background with subtle pattern
        with self.root.canvas.before:
            Color(*BG_DARK)
            Rectangle(pos=(0, 0), size=Window.size)
        
        main_layout = BoxLayout(orientation='vertical', padding=dp(20), spacing=dp(15))
        
        # Header with lightning effect
        header = BoxLayout(size_hint_y=None, height=dp(80))
        
        title_layout = BoxLayout(orientation='vertical')
        title = Label(
            text='[color=ff6b00]⚡[/color] [b]BitcoinMesh[/b]',
            markup=True,
            font_size=dp(28),
            halign='center'
        )
        subtitle = Label(
            text='[color=888899]Broadcast Bitcoin over LoRa[/color]',
            markup=True,
            font_size=dp(12),
            halign='center'
        )
        title_layout.add_widget(title)
        title_layout.add_widget(subtitle)
        header.add_widget(title_layout)
        
        main_layout.add_widget(header)
        
        # Transaction input card
        input_card = BoxLayout(orientation='vertical', spacing=dp(10), size_hint_y=0.4)
        
        input_label = Label(
            text='[color=ff6b00]📝[/color] Transaction Bitcoin (hex)',
            markup=True,
            font_size=dp(14),
            halign='left',
            size_hint_y=None,
            height=dp(30)
        )
        input_label.bind(size=input_label.setter('text_size'))
        
        self.tx_input = NeonTextInput(
            hint_text='Collez votre transaction signée ici...\nExemple: 0100000001abc...',
            multiline=True
        )
        
        # Character counter
        self.char_counter = Label(
            text='[color=888899]0 caractères | 0 chunks[/color]',
            markup=True,
            font_size=dp(12),
            halign='right',
            size_hint_y=None,
            height=dp(25)
        )
        self.char_counter.bind(size=self.char_counter.setter('text_size'))
        self.tx_input.bind(text=self.update_counter)
        
        input_card.add_widget(input_label)
        input_card.add_widget(self.tx_input)
        input_card.add_widget(self.char_counter)
        
        main_layout.add_widget(input_card)
        
        # Action buttons
        btn_layout = BoxLayout(spacing=dp(15), size_hint_y=None, height=dp(50))
        
        self.chunk_btn = NeonButton(text='⚡ DÉCOUPER')
        self.chunk_btn.bind(on_press=self.chunk_transaction)
        
        paste_btn = NeonButton(text='📋 COLLER')
        paste_btn.bind(on_press=self.paste_from_clipboard)
        
        clear_btn = NeonButton(text='🗑️ EFFACER')
        clear_btn.bind(on_press=self.clear_all)
        
        btn_layout.add_widget(paste_btn)
        btn_layout.add_widget(self.chunk_btn)
        btn_layout.add_widget(clear_btn)
        
        main_layout.add_widget(btn_layout)
        
        # Chunks display area
        chunks_label = Label(
            text='[color=ff6b00]📦[/color] Messages à envoyer',
            markup=True,
            font_size=dp(14),
            halign='left',
            size_hint_y=None,
            height=dp(30)
        )
        chunks_label.bind(size=chunks_label.setter('text_size'))
        main_layout.add_widget(chunks_label)
        
        # Scrollable chunk list
        self.chunks_scroll = ScrollView(size_hint_y=0.4)
        self.chunks_layout = BoxLayout(orientation='vertical', spacing=dp(8), size_hint_y=None)
        self.chunks_layout.bind(minimum_height=self.chunks_layout.setter('height'))
        self.chunks_scroll.add_widget(self.chunks_layout)
        
        main_layout.add_widget(self.chunks_scroll)
        
        # Status bar
        self.status = Label(
            text='[color=888899]Prêt - Collez votre transaction[/color]',
            markup=True,
            font_size=dp(12),
            size_hint_y=None,
            height=dp(30)
        )
        main_layout.add_widget(self.status)
        
        # Footer
        footer = Label(
            text='[color=444455]Created by Silexperience & ProfEduStream[/color]',
            markup=True,
            font_size=dp(10),
            size_hint_y=None,
            height=dp(25)
        )
        main_layout.add_widget(footer)
        
        self.root.add_widget(main_layout)
        return self.root
    
    def update_counter(self, instance, value):
        clean_hex = value.replace(' ', '').replace('\n', '').replace('0x', '')
        chars = len(clean_hex)
        chunks = (chars + CHUNK_SIZE - 1) // CHUNK_SIZE if chars > 0 else 0
        
        color = 'ff6b00' if chars > CHUNK_SIZE else '888899'
        self.char_counter.text = f'[color={color}]{chars} caractères | {chunks} chunk{"s" if chunks > 1 else ""}[/color]'
    
    def paste_from_clipboard(self, instance):
        try:
            text = Clipboard.paste()
            if text:
                self.tx_input.text = text
                self.status.text = '[color=00ff88]✅ Collé depuis le presse-papier[/color]'
        except Exception as e:
            self.status.text = f'[color=ff4444]❌ Erreur: {e}[/color]'
    
    def clear_all(self, instance):
        self.tx_input.text = ''
        self.chunks_layout.clear_widgets()
        self.status.text = '[color=888899]Prêt - Collez votre transaction[/color]'
    
    def chunk_transaction(self, instance):
        # Clean the input
        raw = self.tx_input.text
        clean_hex = raw.replace(' ', '').replace('\n', '').replace('\r', '').replace('0x', '')
        
        # Validate hex
        if not clean_hex:
            self.status.text = '[color=ff4444]❌ Aucune transaction à découper[/color]'
            return
        
        if not all(c in '0123456789abcdefABCDEF' for c in clean_hex):
            self.status.text = '[color=ff4444]❌ Format invalide - hexadécimal uniquement[/color]'
            return
        
        # Validate it looks like a Bitcoin TX
        if not (clean_hex.startswith('01') or clean_hex.startswith('02')):
            self.status.text = '[color=ffaa00]⚠️ Attention: ne semble pas être une TX Bitcoin[/color]'
        
        # Create chunks with header format: BTX:total:index:data
        chunks = []
        total_chunks = (len(clean_hex) + CHUNK_SIZE - 1) // CHUNK_SIZE
        
        for i in range(total_chunks):
            start = i * CHUNK_SIZE
            end = start + CHUNK_SIZE
            chunk_data = clean_hex[start:end]
            # Format: BTX:3:1:hexdata (total:index:data)
            formatted = f"BTX:{total_chunks}:{i+1}:{chunk_data}"
            chunks.append(formatted)
        
        # Display chunks
        self.chunks_layout.clear_widgets()
        for i, chunk in enumerate(chunks):
            chunk_widget = ChunkDisplay(i + 1, total_chunks, chunk)
            self.chunks_layout.add_widget(chunk_widget)
        
        self.status.text = f'[color=00ff88]✅ Découpé en {total_chunks} parties - Envoyez dans l\'ordre![/color]'
        
        # Animate button
        anim = Animation(color=GREEN_SUCCESS, duration=0.2) + Animation(color=ORANGE_NEON, duration=0.3)
        anim.start(self.chunk_btn)


if __name__ == '__main__':
    BitcoinMeshApp().run()
