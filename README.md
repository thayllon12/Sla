# Joguinho Space Defender

Jogo 2D Android completo (sprites + áudio + VFX livres).

## Como subir no GitHub (fácil)

1. Extraia o zip
2. Entre na pasta **Pastinha**
3. Selecione **todos os arquivos e pastas** que estão dentro dela
4. Arraste tudo para um repositório novo no GitHub (ou faça upload)

A estrutura que vai ficar no GitHub é:

```
.github/workflows/build.yml
app/
build.gradle
settings.gradle
gradle.properties
README.md
```

## Gerar o APK

1. Vá em **Actions**
2. Clique em **Build APK** → **Run workflow**
3. Baixe o artefato **app-debug-apk**

## Controles

- Toque e arraste para mover a nave
- Disparo automático
- Pegue os powerups verdes

## Observação

Toda a estrutura de pastas (`app/src/main/...`) é necessária para o Android funcionar. Não é possível deixar os arquivos .java soltos na raiz sem quebrar a compilação.
