# SOL PIXEL ☀️ — jogo Android 3D procedural

Protótipo de exploração em **primeira pessoa**, nativo para Android, feito com **Java + OpenGL ES 3.0**, sem dependência de engine ou de servidor.

## Funcionalidades

- Tela inicial **PLAY**, cenário renderizado ao fundo.
- Joystick analógico na **esquerda** para caminhar.
- **Botão grande PULAR** na direita; gravidade e salto.
- Câmera em 360° arrastando o dedo na parte livre da tela.
- **Botão de pausa pequeno** e centralizado no topo; menu Continuar / Menu.
- Terreno 3D gerado por ruído, infinito na prática (malha reconstruída em volta do jogador).
- Solo texturizado com **a imagem fornecida pelo usuário**: `app/src/main/assets/ground.jpg`. A versão integral também está em `ground_original.jpg`.
- Árvores blocadas, pedras e sombras projetadas em quads; iluminação quantizada estilo pixel art.
- Céu com posição solar direcional, corona, nuvens, *god rays* / raios visíveis com oclusão por profundidade aproximada.
- Pós-processamento GPU com *bloom* por extração de luminosidade + blur horizontal/vertical.
- *Depth of Field* (desfoque por distância da câmera) em pós-processamento.
- Poeira flutuante aleatória, translúcida, renderizada como partículas.
- Cenário reproduzível: os mesmos pontos têm o mesmo relevo e vegetação.

> Este é um protótipo visual/jogável. Sombras, raios e DOF são técnicas **estilizadas/aproximadas** para economizar processamento móvel, não ray tracing nem simulação física volumétrica. Ainda não há missões, áudio ou inventário.

## Compilar no GitHub — só pelo celular

1. Extraia o ZIP na pasta `Download` do Android.
2. Entre no **Termux**, onde você já usa o `git` e o `gh`, e execute:

   ```bash
   termux-setup-storage  # so precisa executar uma vez
   pkg install git gh -y
   cd ~/storage/downloads/SolPixel
   git init -b main
   git add .
   git commit -m "Sol Pixel v0.1 - jogo 3D Android"
   gh auth status
   gh repo create SolPixel --public --source=. --remote=origin --push
   ```

   Se `gh repo create` indicar que o repositório já existe, conecte o remoto existente:

   ```bash
   gh repo view --json nameWithOwner --jq .nameWithOwner
   git remote add origin https://github.com/SEU_USUARIO/SolPixel.git
   git push -u origin main
   ```

3. Abra `https://github.com/SEU_USUARIO/SolPixel/actions`.
4. Abra o workflow **Compilar APK Sol Pixel**, aguarde a execução e baixe o artefato **SolPixel-APK**.
5. Extraia o artefato e instale o arquivo **app-debug.apk** no celular.

O workflow também pode ser acionado em **Actions > Compilar APK Sol Pixel > Run workflow**.

### Como atualizar a textura

Substitua `app/src/main/assets/ground.jpg` por outro JPG e envie o commit ao GitHub. O renderizador usa `GL_MIRRORED_REPEAT` e filtro `NEAREST` para manter o efeito blocado sem costuras fortes.

### Onde alterar os controles e os efeitos

| Arquivo | Responsabilidade |
| --- | --- |
| `GameHud.java` | Botões, joystick, toque e menus |
| `GameRenderer.java` | OpenGL, câmera, movimentação, gravidade, passes de renderização |
| `World.java` | Relevo procedural, árvores, pedras e geometrias de sombra |
| `Shaders.java` | Céu, sol, luz, bloom, DOF, poeira e raios |
| `.github/workflows/android.yml` | Compilação automática e publicação do APK |

Renderização interna limitada a **1280 pixels no maior lado**. Para reduzir o custo em celulares fracos, troque `1280f` por `960f` na linha de cálculo de escala em `GameRenderer.onSurfaceChanged`. Isso reduzirá a definição visual e o consumo de GPU.

## Requisitos

- Android 8.0 ou superior (API 26)
- Dispositivo compatível com OpenGL ES 3.0
- Orientação horizontal / landscape
- O **APK de debug** não exige keystore pessoal; é instalado para testes, não serve como publicação assinada em loja.

## Build local alternativo (PC com SDK Android e Gradle 8.11.1)

```bash
gradle :app:assembleDebug
```

Saída: `app/build/outputs/apk/debug/app-debug.apk`.

## Tecnologias de build travadas

- JDK 17
- Android Gradle Plugin 8.9.2
- Gradle 8.11.1
- Android SDK 35

## Estado dos testes

Os arquivos Java foram verificados por compilação sintática com *stubs*, e o gerador procedural foi executado com dados de teste. A compilação completa de um APK e os testes gráficos em GPU Android dependem da execução do workflow do GitHub ou de Android Studio/SDK, não disponíveis no ambiente de geração.
