# ==============================================================================
# Script de Deploy Automatizado — EcoWatch Brasil no Azure Container Apps
# ==============================================================================

param (
    [string]$ResourceGroup = "ecowatch-rg",
    [string]$Location = "brazilsouth",
    [string]$AcrName = "ecowatchacr$((Get-Random -Minimum 1000 -Maximum 9999))",
    [string]$EventHubNamespace = "ecowatch-hub-$((Get-Random -Minimum 1000 -Maximum 9999))",
    [string]$EnvironmentName = "ecowatch-env"
)

Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "🚀 INICIANDO DEPLOY DO ECOWATCH BRASIL NO AZURE" -ForegroundColor Cyan
Write-Host "   Resource Group: $ResourceGroup | Região: $Location" -ForegroundColor Gray
Write-Host "   Container Registry: $AcrName" -ForegroundColor Gray
Write-Host "   Event Hubs (Kafka): $EventHubNamespace" -ForegroundColor Gray
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan

# 1. Verificar Login
Write-Host "`n[1/7] 🔑 Verificando autenticação no Azure..." -ForegroundColor Yellow
$account = az account show --query name -o tsv 2>$null
if (-not $account) {
    Write-Host "Você precisa fazer login no Azure. Abrindo navegador..." -ForegroundColor Yellow
    az login
}
$sub = az account show --query name -o tsv
Write-Host "✅ Conectado na assinatura: $sub" -ForegroundColor Green

# 2. Criar Resource Group
Write-Host "`n[2/7] 📦 Criando Resource Group '$ResourceGroup'..." -ForegroundColor Yellow
az group create --name $ResourceGroup --location $Location --output none
Write-Host "✅ Resource Group pronto!" -ForegroundColor Green

# 3. Criar Azure Container Registry (ACR)
Write-Host "`n[3/7] 🐳 Criando Container Registry '$AcrName'..." -ForegroundColor Yellow
az acr create --resource-group $ResourceGroup --name $AcrName --sku Basic --admin-enabled true --output none
$acrServer = az acr show --name $AcrName --query loginServer -o tsv
Write-Host "✅ ACR criado com sucesso: $acrServer" -ForegroundColor Green

# 4. Criar Azure Event Hubs (Kafka Gerenciado)
Write-Host "`n[4/7] 📨 Criando Azure Event Hubs compatível com Kafka..." -ForegroundColor Yellow
az eventhubs namespace create --name $EventHubNamespace --resource-group $ResourceGroup --location $Location --sku Basic --enable-kafka true --output none
az eventhubs eventhub create --name "weather-readings" --namespace-name $EventHubNamespace --resource-group $ResourceGroup --output none
$eventHubConnString = az eventhubs namespace authorization-rule keys list --resource-group $ResourceGroup --namespace-name $EventHubNamespace --name RootManageSharedAccessKey --query primaryConnectionString -o tsv
$kafkaBootstrap = "$EventHubNamespace.servicebus.windows.net:9093"
$jaasConfig = "org.apache.kafka.common.security.plain.PlainLoginModule required username=`"`$ConnectionString`" password=`"$eventHubConnString`";"
Write-Host "✅ Event Hubs criado! Endpoint Kafka: $kafkaBootstrap" -ForegroundColor Green

# 5. Criar Container Apps Environment
Write-Host "`n[5/7] 🌐 Criando Container Apps Environment '$EnvironmentName'..." -ForegroundColor Yellow
az containerapp env create --name $EnvironmentName --resource-group $ResourceGroup --location $Location --output none
Write-Host "✅ Container Apps Environment pronto!" -ForegroundColor Green

# 6. Criar RabbitMQ Interno
Write-Host "`n[6/7] 🐰 Subindo RabbitMQ como serviço interno..." -ForegroundColor Yellow
az containerapp create `
  --name "rabbitmq" `
  --resource-group $ResourceGroup `
  --environment $EnvironmentName `
  --image "rabbitmq:3-management-alpine" `
  --target-port 5672 `
  --ingress internal `
  --min-replicas 1 `
  --max-replicas 1 `
  --cpu 0.25 `
  --memory 0.5Gi `
  --env-vars RABBITMQ_DEFAULT_USER=guest RABBITMQ_DEFAULT_PASS=guest `
  --output none
Write-Host "✅ RabbitMQ interno pronto no host: rabbitmq" -ForegroundColor Green

# 7. Compilar e Publicar Imagens no ACR (Cloud Build)
Write-Host "`n[7/7] ⚙️ Compilando os 5 microsserviços diretamente na nuvem (ACR Build)..." -ForegroundColor Yellow
$services = @(
    @{ Name="data-ingestion"; Dir="./data-ingestion-service" },
    @{ Name="alert-service"; Dir="./alert-service" },
    @{ Name="read-model"; Dir="./read-model-service" },
    @{ Name="notification-service"; Dir="./notification-service" },
    @{ Name="analytics-service"; Dir="./analytics-service" }
)

foreach ($svc in $services) {
    Write-Host "🔨 Compilando imagem para $($svc.Name)..." -ForegroundColor Cyan
    az acr build --registry $AcrName --image "$($svc.Name):latest" $svc.Dir --output none
    Write-Host "✅ $($svc.Name) compilado e publicado no ACR!" -ForegroundColor Green
}

Write-Host "`n════════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "🎉 INFRAESTRUTURA E IMAGENS PRONTAS NO AZURE!" -ForegroundColor Green
Write-Host "   Próximo passo: implantar os microsserviços via az containerapp create" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Green
