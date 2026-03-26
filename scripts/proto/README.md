# Proto 代码生成脚本说明

本目录包含用于从 `.proto` 定义生成各语言代码的辅助脚本。

这些脚本用于根据 `iarnet-proto` 模块中的协议定义文件，统一生成 **Java、Python 和 Go** 的 protobuf 与 gRPC 代码。通过脚本方式进行代码生成，可以避免将代码生成逻辑绑定到 Maven 生命周期中，使生成流程更加灵活，并方便多语言统一管理。

该设计实现了以下目标：

* `.proto` 文件作为 **唯一的协议源（Single Source of Truth）**
* 不同语言的生成代码分别存放在各自模块中
* 手写扩展代码与自动生成代码保持清晰分离

---

# 目录结构

```text
scripts/proto/
├─ generate.sh
├─ clean.sh
├─ generate-java.sh
├─ generate-python.sh
└─ generate-go.sh
```

各脚本功能如下：

| 脚本                   | 作用                             |
| -------------------- | ------------------------------ |
| `generate.sh`        | 一次性生成所有语言的 proto 代码            |
| `generate-java.sh`   | 生成 Java 的 protobuf 和 gRPC 代码   |
| `generate-python.sh` | 生成 Python 的 protobuf 和 gRPC 代码 |
| `generate-go.sh`     | 生成 Go 的 protobuf 和 gRPC 代码     |
| `clean.sh`           | 清理所有语言生成的代码                    |

---

# Proto 文件位置

所有 `.proto` 文件位于：

```text
iarnet-proto/proto/
```

该目录中的文件是整个系统的 **协议定义源文件**。

生成代码会被写入语言对应的模块目录，例如：

| 语言     | 输出目录                                                     |
| ------ | -------------------------------------------------------- |
| Java   | `iarnet-proto-lang/iarnet-proto-java/src/main/generated` |
| Python | `iarnet-proto-lang/iarnet-proto-python/gen`              |
| Go     | `iarnet-proto-lang/iarnet-proto-go/gen`                  |

---

# 使用方法

所有脚本都需要在 **仓库根目录**执行。

## 生成所有语言代码

```bash
./scripts/proto/generate.sh
```

该命令会依次调用各语言生成脚本。

---

## 只生成某一种语言

### Java

```bash
./scripts/proto/generate-java.sh
```

### Python

```bash
./scripts/proto/generate-python.sh
```

### Go

```bash
./scripts/proto/generate-go.sh
```

---

## 清理生成代码

```bash
./scripts/proto/clean.sh
```

该命令会删除各语言目录下的生成文件，但保留目录结构。

---

# 环境依赖

在运行脚本之前，需要安装以下工具。

## protoc

protobuf 编译器，用于将 `.proto` 编译为各语言代码。

**验证是否已安装：**

```bash
protoc --version
```

**安装方法：**

* **Linux（Debian/Ubuntu）：**
  ```bash
  sudo apt update
  sudo apt install -y protobuf-compiler
  ```

* **Linux（其他发行版）：** 从 [Protocol Buffers releases](https://github.com/protocolbuffers/protobuf/releases) 下载对应平台的 `protoc-<version>-<os>-<arch>.zip`，解压后将 `bin/protoc` 放到 `PATH` 中。

* **macOS：**
  ```bash
  brew install protobuf
  ```

* **Windows：** 从 [releases](https://github.com/protocolbuffers/protobuf/releases) 下载 `protoc-<version>-win64.zip`，解压后将 `bin` 目录加入系统 `PATH`；或使用 Chocolatey：`choco install protoc`。

---

## Java gRPC 插件

用于生成 Java gRPC stub 的 `protoc` 插件，可执行名为 `protoc-gen-grpc-java`。

**验证是否可用：** 在终端执行 `protoc-gen-grpc-java` 能运行（可能无输出），且其所在目录在 `PATH` 中。

**安装方法：**

按系统选择以下命令之一（将 `VERSION` 替换为所需版本，如 `1.62.2`；安装目录可改为 `~/bin`、`/usr/local/bin` 等并确保在 `PATH` 中）：

```bash
# Linux x86_64
VERSION=1.62.2
curl -L -o /usr/local/bin/protoc-gen-grpc-java "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/${VERSION}/protoc-gen-grpc-java-${VERSION}-linux-x86_64.exe"
chmod +x /usr/local/bin/protoc-gen-grpc-java
```

```bash
# macOS（Apple Silicon 将 x86_64 改为 aarch_64）
VERSION=1.62.2
curl -L -o /usr/local/bin/protoc-gen-grpc-java "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/${VERSION}/protoc-gen-grpc-java-${VERSION}-osx-x86_64.exe"
chmod +x /usr/local/bin/protoc-gen-grpc-java
```

```powershell
# Windows（PowerShell，请将 $env:VERSION 和安装目录改为实际路径并加入 PATH）
$VERSION = "1.62.2"
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/$VERSION/protoc-gen-grpc-java-$VERSION-windows-x86_64.exe" -OutFile "$env:LOCALAPPDATA\protoc-gen-grpc-java.exe" -UseBasicParsing
# 将 $env:LOCALAPPDATA 加入系统 PATH，或把生成的 exe 放到已在 PATH 中的目录
```

也可从 [Maven Central](https://central.sonatype.com/artifact/io.grpc/protoc-gen-grpc-java) 或 [grpc-java releases](https://github.com/grpc/grpc-java/releases) 手动下载对应平台的可执行文件，重命名为 `protoc-gen-grpc-java`（Windows 保留 `.exe`），放入 `PATH` 中某目录。

若项目已通过 Maven 使用该插件，本地仓库中会有 `~/.m2/repository/io/grpc/protoc-gen-grpc-java/<version>/` 下各平台 exe，可复制到 `PATH` 中并命名为 `protoc-gen-grpc-java` 使用。

---

## Python 依赖

生成 Python 代码需要 `grpcio-tools` 与 `protobuf`（提供 `grpc_python_plugin` 与 protobuf 运行时）。

**安装方法：**

```bash
python -m pip install grpcio-tools protobuf
```

建议在虚拟环境中安装：`python -m venv .venv && source .venv/bin/activate`（Windows 下为 `.venv\Scripts\activate`），再执行上述 `pip install`。

---

## Go 依赖

生成 Go 代码需要 `protoc-gen-go` 与 `protoc-gen-go-grpc` 两个插件。

**安装方法：**

```bash
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
```

安装后插件位于 `$GOPATH/bin`（Go 1.8+ 默认为 `$HOME/go/bin`），请将该目录加入 `PATH`，例如在 `~/.bashrc` 或 `~/.zshrc` 中添加：`export PATH="$PATH:$(go env GOPATH)/bin"`。

---

# 开发流程

当修改 `.proto` 文件后，建议按照以下流程进行开发：

1. 修改 `iarnet-proto/proto` 目录下的 `.proto` 文件
2. 运行代码生成脚本：

```bash
./scripts/proto/generate.sh
```

3. 重新编译相关模块

---

# 注意事项

* **不要手动修改生成代码。**
  生成代码会在下一次执行生成脚本时被覆盖。

* 如果需要添加额外逻辑，应在各语言模块的 **support 层** 中实现，而不是修改生成代码。

* `.proto` 文件应保持 **语言无关（language-agnostic）**。

---

# 设计原则

本项目将 proto 相关代码划分为三个层次：

```text
.proto 协议定义
        ↓
自动生成的语言绑定代码
        ↓
各语言手写的支持/扩展层
```

这种结构具有以下优点：

* 协议定义集中管理
* 生成代码可重复构建
* 语言扩展逻辑与生成代码解耦

---

# 未来可能的改进

未来可以考虑进一步完善 proto 管理体系，例如：

* 引入 **Buf** 进行 proto lint 和生成管理
* 在 CI 中自动校验 proto 兼容性
* 自动执行 proto 生成流程
* 发布版本化的 proto SDK
