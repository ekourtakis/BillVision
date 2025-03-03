from training.train import train_model
import os

if __name__ == '__main__':
    # Create models directory if it doesn't exist
    os.makedirs('models', exist_ok=True)
    
    # Train and save the model
    model_path = 'models/bill_classifier.keras'
    model = train_model(model_path) 