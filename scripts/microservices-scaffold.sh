# 1. Catálogo (Reactivo)
mkdir ms-catalog
cd ms-catalog
echo "plugins {
    id 'co.com.bancolombia.cleanArchitecture' version '4.2.0'
}" > build.gradle
gradle wrapper
./gradlew ca --package=com.arka --type=reactive --name=ms-catalog --java-version=21
cd ..

# 2. Inventario (Reactivo)
mkdir ms-inventory
cd ms-inventory
echo "plugins {
    id 'co.com.bancolombia.cleanArchitecture' version '4.2.0'
}" > build.gradle
gradle wrapper
./gradlew ca --package=com.arka --type=reactive --name=ms-inventory --java-version=21
cd ..

# 3. Órdenes (Reactivo)
mkdir ms-order
cd ms-order
echo "plugins {
    id 'co.com.bancolombia.cleanArchitecture' version '4.2.0'
}" > build.gradle
gradle wrapper
./gradlew ca --package=com.arka --type=reactive --name=ms-order --java-version=21
cd ..

# 4. Carrito (Reactivo)
mkdir ms-cart
cd ms-cart
echo "plugins {
    id 'co.com.bancolombia.cleanArchitecture' version '4.2.0'
}" > build.gradle
gradle wrapper
./gradlew ca --package=com.arka --type=reactive --name=ms-cart --java-version=21
cd ..

# 5. Notificaciones (Reactivo)
mkdir ms-notifications
cd ms-notifications
echo "plugins {
    id 'co.com.bancolombia.cleanArchitecture' version '4.2.0'
}" > build.gradle
gradle wrapper
./gradlew ca --package=com.arka --type=reactive --name=ms-notifications --java-version=21
cd ..

# 6. Pagos (Imperativo)
mkdir ms-payment
cd ms-payment
echo "plugins {
    id 'co.com.bancolombia.cleanArchitecture' version '4.2.0'
}" > build.gradle
gradle wrapper
./gradlew ca --package=com.arka --type=reactive --name=ms-payment --java-version=21
cd ..

# 7. Envíos (Imperativo)
mkdir ms-shipping
cd ms-shipping
echo "plugins {
    id 'co.com.bancolombia.cleanArchitecture' version '4.2.0'
}" > build.gradle
gradle wrapper
./gradlew ca --package=com.arka --type=reactive --name=ms-shipping --java-version=21
cd ..

# 8. Proveedores (Imperativo)
mkdir ms-provider
cd ms-provider
echo "plugins {
    id 'co.com.bancolombia.cleanArchitecture' version '4.2.0'
}" > build.gradle
gradle wrapper
./gradlew ca --package=com.arka --type=reactive --name=ms-provider --java-version=21
cd ..

# 9. Reportería (Imperativo)
mkdir ms-reporter
cd ms-reporter
echo "plugins {
    id 'co.com.bancolombia.cleanArchitecture' version '4.2.0'
}" > build.gradle
gradle wrapper
./gradlew ca --package=com.arka --type=imperative --name=ms-reporter --java-version=21
cd ..
